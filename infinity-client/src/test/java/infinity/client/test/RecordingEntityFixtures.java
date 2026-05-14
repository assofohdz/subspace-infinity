// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.test;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityChange;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityCriteria;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.WatchedEntity;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.es.ComponentFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Recording wrappers for {@link EntitySet} / {@link WatchedEntity} so tests can
 * assert leak-fix discipline (each acquire matches a release). See
 * {@code .claude/rules/entity-sets.md}.
 */
public final class RecordingEntityFixtures {

  private RecordingEntityFixtures() { /* static factory */ }

  /** {@link DefaultEntityData} that wraps every {@link EntitySet} / {@link WatchedEntity} it returns. */
  @SuppressWarnings("PMD.LooseCoupling") // RecordingEntitySet is the test-internal recording surface; tests need its release-count accessors
  public static final class RecordingEntityData extends DefaultEntityData {
    private final List<RecordingEntitySet> entitySets = new ArrayList<>();
    private final List<RecordingWatchedEntity> watched = new ArrayList<>();

    @Override
    public EntitySet getEntities(final Class... types) {
      return record(super.getEntities(types));
    }

    @Override
    public EntitySet getEntities(final ComponentFilter filter, final Class... types) {
      return record(super.getEntities(filter, types));
    }

    @Override
    public EntitySet getEntities(final EntityCriteria criteria) {
      return record(super.getEntities(criteria));
    }

    @Override
    public WatchedEntity watchEntity(final EntityId id, final Class... types) {
      final RecordingWatchedEntity rw = new RecordingWatchedEntity(super.watchEntity(id, types));
      watched.add(rw);
      return rw;
    }

    private EntitySet record(final EntitySet delegate) {
      final RecordingEntitySet rs = new RecordingEntitySet(delegate);
      entitySets.add(rs);
      return rs;
    }

    /** Snapshot of every {@link EntitySet} this data has handed out. */
    public List<RecordingEntitySet> entitySets() {
      return Collections.unmodifiableList(entitySets);
    }

    /** Snapshot of every {@link WatchedEntity} this data has handed out. */
    public List<RecordingWatchedEntity> watchedEntities() {
      return Collections.unmodifiableList(watched);
    }
  }

  /** Wraps an {@link EntitySet}, counting {@link #release()} invocations. */
  public static final class RecordingEntitySet implements EntitySet {
    private final EntitySet delegate;
    private int releaseCount;

    RecordingEntitySet(final EntitySet delegate) { this.delegate = delegate; }

    public int releaseCount() { return releaseCount; }
    public boolean wasReleased() { return releaseCount > 0; }

    @Override public void release() {
      releaseCount++;
      delegate.release();
    }

    // --- pass-through ------------------------------------------------------

    @Override public void resetFilter(final ComponentFilter filter) { delegate.resetFilter(filter); }
    @Override public void resetEntityCriteria(final EntityCriteria criteria) { delegate.resetEntityCriteria(criteria); }
    @Override public boolean containsId(final EntityId id) { return delegate.containsId(id); }
    @Override public Set<EntityId> getEntityIds() { return delegate.getEntityIds(); }
    @Override public Entity getEntity(final EntityId id) { return delegate.getEntity(id); }
    @Override public Set<Entity> getAddedEntities() { return delegate.getAddedEntities(); }
    @Override public Set<Entity> getChangedEntities() { return delegate.getChangedEntities(); }
    @Override public Set<Entity> getRemovedEntities() { return delegate.getRemovedEntities(); }
    @Override public void clearChangeSets() { delegate.clearChangeSets(); }
    @Override public boolean hasChanges() { return delegate.hasChanges(); }
    @Override public boolean applyChanges() { return delegate.applyChanges(); }
    @SuppressWarnings("deprecation")
    @Override public boolean applyChanges(final Set<EntityChange> updates) { return delegate.applyChanges(updates); }
    @Override public boolean hasType(final Class type) { return delegate.hasType(type); }
    @Override public int size() { return delegate.size(); }
    @Override public boolean isEmpty() { return delegate.isEmpty(); }
    @Override public boolean contains(final Object o) { return delegate.contains(o); }
    @Override public Iterator<Entity> iterator() { return delegate.iterator(); }
    @Override public Object[] toArray() { return delegate.toArray(); }
    @Override public <T> T[] toArray(final T[] a) { return delegate.toArray(a); }
    @Override public boolean add(final Entity e) { return delegate.add(e); }
    @Override public boolean remove(final Object o) { return delegate.remove(o); }
    @Override public boolean containsAll(final java.util.Collection<?> c) { return delegate.containsAll(c); }
    @Override public boolean addAll(final java.util.Collection<? extends Entity> c) { return delegate.addAll(c); }
    @Override public boolean retainAll(final java.util.Collection<?> c) { return delegate.retainAll(c); }
    @Override public boolean removeAll(final java.util.Collection<?> c) { return delegate.removeAll(c); }
    @Override public void clear() { delegate.clear(); }
  }

  /** Wraps a {@link WatchedEntity}, counting {@link #release()} invocations. */
  public static final class RecordingWatchedEntity implements WatchedEntity {
    private final WatchedEntity delegate;
    private int releaseCount;

    RecordingWatchedEntity(final WatchedEntity delegate) { this.delegate = delegate; }

    public int releaseCount() { return releaseCount; }
    public boolean wasReleased() { return releaseCount > 0; }

    @Override public void release() {
      releaseCount++;
      delegate.release();
    }

    // --- pass-through ------------------------------------------------------

    @Override public boolean hasChanges() { return delegate.hasChanges(); }
    @Override public boolean applyChanges() { return delegate.applyChanges(); }
    @Override public boolean applyChanges(final Set<EntityChange> updates) { return delegate.applyChanges(updates); }
    @Override public EntityId getId() { return delegate.getId(); }
    @Override public <T extends EntityComponent> T get(final Class<T> type) { return delegate.get(type); }
    @Override public void set(final EntityComponent c) { delegate.set(c); }
    @Override public boolean isComplete() { return delegate.isComplete(); }
    @Override public EntityComponent[] getComponents() { return delegate.getComponents(); }
  }
}
