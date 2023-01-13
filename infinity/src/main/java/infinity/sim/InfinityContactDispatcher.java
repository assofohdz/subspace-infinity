package infinity.sim;

import com.google.common.reflect.TypeToken;
import com.simsilica.mphys.*;

public class InfinityContactDispatcher<K, S extends AbstractShape>  implements ContactListener {

    private final DynArray<ContactListener<K, S>> listeners = new DynArray<>(new TypeToken<ContactListener<K, S>>() {});

    @Override
    public void newContact(Contact contact) {
        for(ContactListener l : listeners){
            l.newContact(contact);
        }
    }

    public void addListener(ContactListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(ContactListener listener) {
        this.listeners.remove(listener);
    }
}
