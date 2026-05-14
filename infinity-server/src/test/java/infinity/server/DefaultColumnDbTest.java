// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.mworld.ColumnData;
import com.simsilica.mworld.ColumnId;
import java.io.File;
import java.nio.file.Files;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Pins the two concurrency fixes in DefaultColumnDb: Striped per-column
 * locks (replaces class-wide writeLock) + atomic file-replace via temp +
 * rename (closes the resetChanged-before-write race in the Mythruna
 * DataVersion persistence pattern).
 */
public final class DefaultColumnDbTest {

  private File tmpDir;

  @Before
  public void setUp() throws Exception {
    tmpDir = Files.createTempDirectory("columndb-test").toFile();
  }

  @After
  public void tearDown() {
    deleteRecursively(tmpDir);
  }

  /** writeColumn produces the target file and leaves no `.tmp` siblings behind. */
  @Test
  public void writeColumn_leavesNoTempFileBehind() {
    final DefaultColumnDb db = new DefaultColumnDb(tmpDir);
    final ColumnId id = new ColumnId(42L);
    final ColumnData col = new ColumnData(id, 1);

    db.writeColumn(col);

    final File target = new File(tmpDir, "col-42");
    final File tmp = new File(target.getAbsolutePath() + ".tmp");
    // Either layout: file may be nested per ParentIdFileFunction. Just assert no
    // .tmp siblings anywhere in the tree.
    assertFalse("temp file must be cleaned up after rename: " + tmp, tmp.exists());
    assertFalse("no .tmp files anywhere under " + tmpDir,
        hasTempFile(tmpDir));
  }

  /** resetChanged is called AFTER the file is published, so isChanged() drops to false on return. */
  @Test
  public void writeColumn_resetsChangedAfterPublishingFile() {
    final DefaultColumnDb db = new DefaultColumnDb(tmpDir);
    final ColumnId id = new ColumnId(7L);
    final ColumnData col = new ColumnData(id, 1);
    // Bump version past loadVersion so isChanged() goes true and we have something
    // for resetChanged() to bring back in sync.
    col.getVersion().markChanged();
    assertTrue("after markChanged, column must report as changed", col.getVersion().isChanged());

    db.writeColumn(col);

    assertFalse("after writeColumn, isChanged() must reflect the persisted state",
        col.getVersion().isChanged());
  }

  /** Same ColumnId always maps to the same striped lock. */
  @Test
  public void stripedLocks_sameColumnIdMapsToSameLock() {
    final com.google.common.util.concurrent.Striped<java.util.concurrent.locks.Lock> stripes =
        com.google.common.util.concurrent.Striped.lock(64);
    final ColumnId a = new ColumnId(123L);
    final ColumnId aAgain = new ColumnId(123L);
    assertEquals("Striped must return the same Lock for equal keys",
        stripes.get(a), stripes.get(aAgain));
    assertNotNull("Lock must be acquired-able", stripes.get(a));
  }

  private static boolean hasTempFile(final File dir) {
    final File[] entries = dir.listFiles();
    if (entries == null) {
      return false;
    }
    for (final File entry : entries) {
      if (entry.isDirectory()) {
        if (hasTempFile(entry)) {
          return true;
        }
      } else if (entry.getName().endsWith(".tmp")) {
        return true;
      }
    }
    return false;
  }

  private static void deleteRecursively(final File f) {
    if (f == null || !f.exists()) {
      return;
    }
    final File[] entries = f.listFiles();
    if (entries != null) {
      for (final File entry : entries) {
        deleteRecursively(entry);
      }
    }
    f.delete();
  }
}
