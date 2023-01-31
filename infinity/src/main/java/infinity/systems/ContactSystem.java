/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package infinity.systems;

import com.google.common.reflect.TypeToken;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.AbstractShape;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.DynArray;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.CollisionCategory;
import infinity.es.Ghost;
import infinity.es.Parent;
import infinity.sim.CategoryFilter;
import infinity.sim.util.InfinityRunTimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A conctact system to handle the contacts we want to disable as the last delegate in the line.
 *
 * @author AFahrenholz
 */
public class ContactSystem<K, S extends AbstractShape> extends AbstractGameSystem
    implements ContactListener<EntityId, MBlockShape> {

  static Logger log = LoggerFactory.getLogger(ContactSystem.class);
  private final DynArray<ContactListener<K, S>> listeners =
      new DynArray<>(new TypeToken<ContactListener<K, S>>() {});
  EntitySet categoryFilters;
  private EntityData ed;
  private MPhysSystem<?> physics;

  @Override
  public void newContact(Contact contact) {
    final RigidBody<EntityId, MBlockShape> bodyOne = contact.body1;
    final AbstractBody<EntityId, MBlockShape> bodyTwo = contact.body2;

    // Body1 is always a rigidbody
    // If body two is not null, we are dealing with a collision between a rigidbody (body1) and a
    // rigidbody or a staticbody (body2)
    if (bodyTwo != null) {
      final EntityId one = bodyOne.id;
      final EntityId two = bodyTwo.id;

      if (!categoryFilterAllowsContact(one, two)) {
        log.debug("Disabling contact because of category filters {0}", contact);
        contact.disable();
        return;
      }

      if (parentChildContact(one, two)) {
        log.debug("Disabling contact because of parent-child {0}", contact);
        contact.disable();
        return;
      }

      if (ghostContact(one, two)) {
        log.debug("Disabling contact because one or two is a ghost: {0}", contact);
        contact.disable();
        return;
      }

      log.debug("Collision between: " + bodyOne + " and " + bodyTwo);

    } else {
      log.debug("Collided: {1}  with null", bodyOne);
      // Restitution should make sure the bounce conserves the energy completely
      contact.restitution = 1;
    }

    // Now that we have filtered the basics, lets send it to the various systems listening for
    // contacts
    for (ContactListener l : listeners) {
      l.newContact(contact);
    }
  }

  private boolean categoryFilterAllowsContact(EntityId one, EntityId two) {
    if (categoryFilters.containsId(two) && categoryFilters.containsId(one)) {
      final CategoryFilter filterOne =
          categoryFilters.getEntity(one).get(CollisionCategory.class).getFilter();
      final CategoryFilter filterTwo =
          categoryFilters.getEntity(two).get(CollisionCategory.class).getFilter();

      return filterTwo.isAllowed(filterOne);
    }
    return false;
  }

  private boolean ghostContact(EntityId one, EntityId two) {
    final Ghost ghostOne = ed.getComponent(one, Ghost.class);
    final Ghost ghostTwo = ed.getComponent(two, Ghost.class);
    return ghostOne != null || ghostTwo != null;
  }

  private boolean parentChildContact(EntityId one, EntityId two) {
    final Parent parentOfOne = ed.getComponent(one, Parent.class);
    if (parentOfOne != null && parentOfOne.getParentEntityId().compareTo(two) == 0) {
      return true;
    }

    final Parent parentOfTwo = ed.getComponent(two, Parent.class);
    return parentOfTwo != null && parentOfTwo.getParentEntityId().compareTo(one) == 0;
  }

  @Override
  public void update(final SimTime time) {
    super.update(time); // To change body of generated methods, choose Tools | Templates.

    categoryFilters.applyChanges();
  }

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    if (ed == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires an EntityData object.");
    }
    physics = getSystem(MPhysSystem.class);
    if (physics == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires the MPhysSystem system.");
    }

    categoryFilters = ed.getEntities(CollisionCategory.class);
  }

  @Override
  protected void terminate() {
    categoryFilters.release();
    categoryFilters = null;
  }

  public void addListener(ContactListener listener) {
    this.listeners.add(listener);
  }

  public void removeListener(ContactListener listener) {
    this.listeners.remove(listener);
  }
}
