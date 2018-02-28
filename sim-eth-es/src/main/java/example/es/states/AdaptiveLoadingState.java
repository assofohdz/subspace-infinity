/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es.states;

import com.simsilica.event.EventBus;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.AdaptiveClassLoader;
import example.Main;
import example.sim.BaseGameModule;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ini4j.Ini;

/**
 * This is the state that will load, instantiate, enable/disable, null and
 * remove states dynamically. Should hook into the chat in order to load/disable
 * mods on the fly
 *
 * @author Asser
 */
public class AdaptiveLoadingState extends AbstractGameSystem {

    //A map of settings (key,value) per class loaded
    private HashMap<Object, Ini> classSettings;
    // Create GroovyClassLoader.
    AdaptiveClassLoader classLoader;
    //final GroovyClassLoader classLoader = new GroovyClassLoader();
    private String[] directories;
    private final Vector<File> repository;

    //Used in distribution
    private String modLocation = "modules\\modules.jar";
    //Used from SDK
    private String modLocation2 = "build\\modules\\libs\\modules.jar";

    public AdaptiveLoadingState() {
        repository = new Vector<File>();
        classSettings = new HashMap<>();
    }

    @Override
    protected void initialize() {

        File file = new File(modLocation);
        directories = file.list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });

        //for (String s : directories) {
        try {
            File f1 = new File(modLocation);
            if (f1.exists()) {
                repository.add(f1);
            }
            File f2 = new File(modLocation2);
            if (f2.exists()) {
                repository.add(f2);
            }
            this.classLoader = new AdaptiveClassLoader(repository);

            load("arena1");
        } catch (IllegalAccessException ex) {
            Logger.getLogger(AdaptiveLoadingState.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            Logger.getLogger(AdaptiveLoadingState.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(AdaptiveLoadingState.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(AdaptiveLoadingState.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(AdaptiveLoadingState.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(AdaptiveLoadingState.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            ex.getCause().printStackTrace();
            Logger.getLogger(AdaptiveLoadingState.class.getName()).log(Level.SEVERE, null, ex);
        }
        //}
    }

    @Override
    protected void terminate() {
    }

    private void load(String className) throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        Ini ini = loadSettings("arena1/arena1.ini");
        BaseGameModule c = loadMod("arena1.arena1", ini);
        classSettings.put(c, ini);
    }

    private BaseGameModule loadMod(String file, Ini settingsFile) throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        Class java = classLoader.loadClass(file);
        Constructor c = java.getConstructor(Ini.class);

        BaseGameModule javaObj = (BaseGameModule) c.newInstance(settingsFile);

        this.getManager().addSystem(javaObj);

        return javaObj;
    }

    //Loads the associated settings ini-file
    private Ini loadSettings(String className) throws IOException {
        InputStream inputStream = classLoader.getResourceAsStream(className);
        Ini ini = new Ini(inputStream);
        return ini;
    }

    @Override
    public void update(SimTime tpf) {

    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

}
