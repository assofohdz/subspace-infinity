/*
 * $Id$
 *
 * Copyright (c) 2021, Simsilica, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.server;

import com.simsilica.mworld.db.AbstractColumnDb;
import com.simsilica.mworld.db.ParentIdFileFunction;
import com.simsilica.mworld.db.SpoolingObjectDb;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Striped;

import com.simsilica.mworld.ColumnData;
import com.simsilica.mworld.ColumnId;
import com.simsilica.mworld.io.ColumnDataProtocol;

/** Backing column-database for world cell storage. */
public class DefaultColumnDb extends AbstractColumnDb {
  static Logger log = LoggerFactory.getLogger(DefaultColumnDb.class);

  private Function<ColumnId, File> fileFunc;
  private ColumnDataProtocol protocol = new ColumnDataProtocol();

  private LoadingCache<ColumnId, ColumnData> cache;

  private SpoolingObjectDb<ColumnId, ColumnData> storage;

  // Per-column write locks (Guava Striped: fixed bucket pool, same ColumnId always
  // maps to the same Lock; unrelated ColumnIds usually hash to different buckets).
  // Replaces the prior class-wide writeLock that serialized every writeColumn across
  // all columns — under heavy multi-arena load that was the bottleneck. 64 stripes
  // is well above the number of columns a typical zone holds active at once, so
  // collisions are rare.
  private static final int LOCK_STRIPES = 64;
  private final Striped<Lock> columnLocks = Striped.lock(LOCK_STRIPES);

  public DefaultColumnDb( final File root) {
    // Use ParentIdFileFunction which handles the directory structure properly
    this(new ParentIdFileFunction<ColumnId>(root, "col"));
  }

  public DefaultColumnDb(final Function<ColumnId, File> fileFunc ) {
    this.fileFunc = fileFunc;

    this.cache = CacheBuilder.newBuilder()
        .maximumSize(1024)
        .build(new ColumnLoader());

    this.storage = new SpoolingObjectDb<ColumnId, ColumnData>("columns") {
      protected ColumnData loadObject( final ColumnId id ) {
        return loadColumn(id);
      }

      protected void storeObject( final ColumnId id, final ColumnData data ) {
        final Lock lock = columnLocks.get(id);
        lock.lock();
        try {
          writeColumn(data);
        } finally {
          lock.unlock();
        }
      }
    };
  }

  @Override
  public void initialize() {
    storage.initialize();
  }

  @Override
  public void terminate() {
    storage.terminate();
  }

  @Override
  public ColumnData getColumn( final ColumnId columnId ) {
    return cache.getUnchecked(columnId);
  }

  @Override
  public void markChanged( final ColumnData col ) {
    storage.update(col.getColumnId(), col);
  }

  protected ColumnData loadColumn( final ColumnId columnId ) {

    // See if we've generated this column before
    File f = fileFunc.apply(columnId);
    if( f.exists() ) {
      return readColumn(f);
    }

    if( log.isDebugEnabled() ) {
      log.debug("generate column(" + columnId + ")");
    }

    return new ColumnData(columnId, 1);
  }

  protected ColumnData readColumn( final File f ) {
    try( BufferedInputStream in = new BufferedInputStream(new GZIPInputStream(Files.newInputStream(f.toPath()))) ) {
      return protocol.read(in);
    } catch( final IOException e ) {
      throw new IllegalStateException("Error reading column:" + f, e);
    }
  }

  protected void writeColumn( final ColumnData col ) {
    final File f = fileFunc.apply(col.getColumnId());
    final File tmp = new File(f.getAbsolutePath() + ".tmp");

    // Write to a temp file, then atomic-rename onto the target. Two effects:
    //   (1) A reader that opens `f` mid-write can never observe a partial /
    //       truncated GZIP stream — the rename is atomic at the filesystem
    //       level, so the file is always either fully-old or fully-new.
    //   (2) resetChanged() (which sets loadVersion = version, i.e. "marks
    //       in-memory as persisted at this version") runs AFTER the rename
    //       succeeds. The prior order (reset BEFORE write) gave readers a
    //       false isChanged()=false window while the file was still
    //       in-flight — the previously-flagged DataVersion
    //       read-after-write race in the Mythruna-derived persistence layer.
    writeColumn(tmp, col);
    try {
      Files.move(tmp.toPath(), f.toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (final IOException e) {
      throw new IllegalStateException("Error publishing column file: " + tmp + " -> " + f, e);
    }
    col.resetChanged(System.currentTimeMillis());
  }

  protected void writeColumn( final File f, final ColumnData col ) {
    long start = System.nanoTime();
    try( BufferedOutputStream out = new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(f.toPath()))) ) {
      protocol.write(col, out);
    } catch( final IOException e ) {
      throw new IllegalStateException("Error writing column:" + f, e);
    }
    long end = System.nanoTime();
    if (log.isInfoEnabled()) {
      log.info("Wrote column [{}] in {} ms", col, (end - start) / 1000000.0);
    }
  }


  protected class ColumnLoader extends CacheLoader<ColumnId, ColumnData> {
    public ColumnData load( final ColumnId id ) {
      return storage.get(id);
    }
  }
}
