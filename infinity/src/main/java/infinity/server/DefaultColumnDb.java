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
import com.simsilica.mworld.db.IdFileFunction;
import com.simsilica.mworld.db.SpoolingObjectDb;
import java.io.*;
import java.util.function.*;
import java.util.zip.*;

import org.slf4j.*;

import com.google.common.cache.*;

import com.simsilica.mworld.*;
import com.simsilica.mworld.io.ColumnDataProtocol;

/**
 *
 *  @author Asser
 */
public class DefaultColumnDb extends AbstractColumnDb {
  static Logger log = LoggerFactory.getLogger(DefaultColumnDb.class);

  private Function<ColumnId, File> fileFunc;
  private ColumnDataProtocol protocol = new ColumnDataProtocol();

  private LoadingCache<ColumnId, ColumnData> cache;

  private SpoolingObjectDb<ColumnId, ColumnData> storage;

  public DefaultColumnDb( File root) {
    // Shifting grid cell coordinates 5 bits means that all of the columns
    // for a particular tile will be in the same directory.
    this(new IdFileFunction<ColumnId>(root, 5, "col"));
  }

  public DefaultColumnDb(Function<ColumnId, File> fileFunc ) {
    this.fileFunc = fileFunc;

    this.cache = CacheBuilder.newBuilder()
        .maximumSize(1024)
        .build(new ColumnLoader());

    this.storage = new SpoolingObjectDb<ColumnId, ColumnData>("columns") {
      protected ColumnData loadObject( ColumnId id ) {
        return loadColumn(id);
      }

      protected void storeObject( ColumnId id, ColumnData data ) {
        // FIXME: column locks instead of hard sync
        synchronized(data) {
          writeColumn(data);
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
  public ColumnData getColumn( ColumnId columnId ) {
    return cache.getUnchecked(columnId);
  }

  @Override
  public void markChanged( ColumnData col ) {
    storage.update(col.getColumnId(), col);
  }

  protected ColumnData loadColumn( ColumnId columnId ) {

    // See if we've generated this column before
    File f = fileFunc.apply(columnId);
    if( f.exists() ) {
      return readColumn(f);
    }

    if( log.isDebugEnabled() ) {
      log.debug("generate column(" + columnId + ")");
    }

    //ColumnData result = generator.apply(columnId);
    //columnGenerated(result);

    ColumnData result = new ColumnData(columnId, 1);

    return result;
  }

  protected ColumnData readColumn( File f ) {
    try( BufferedInputStream in = new BufferedInputStream(new GZIPInputStream(new FileInputStream(f))) ) {
      return protocol.read(in);
    } catch( IOException e ) {
      throw new RuntimeException("Error reading column:" + f, e);
    }
  }

  protected void writeColumn( ColumnData col ) {
    File f = fileFunc.apply(col.getColumnId());

    // Reset the version first so that we write the new version value
    // to the file.
    // FIXME: fix the thread sync issue here that is pretty common with all
    // DataVerison use-cases.
    col.resetChanged(System.currentTimeMillis());
    writeColumn(f, col);
  }

  protected void writeColumn( File f, ColumnData col ) {
    long start = System.nanoTime();
    try( BufferedOutputStream out = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(f))) ) {
      protocol.write(col, out);
    } catch( IOException e ) {
      throw new RuntimeException("Error writing column:" + f, e);
    }
    long end = System.nanoTime();
    log.info("Wrote column [" + col + "] in " + ((end - start)/1000000.0) + " ms");
  }


  protected class ColumnLoader extends CacheLoader<ColumnId, ColumnData> {
    public ColumnData load( ColumnId id ) {
      return storage.get(id);
    }
  }
}

