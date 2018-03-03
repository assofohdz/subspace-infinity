/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es.states;

import com.jme3.network.service.AbstractHostedService;
import com.jme3.network.service.HostedServiceManager;
import com.simsilica.sim.GameSystemManager;
import example.AdaptiveClassLoader;
import example.net.chat.server.ChatHostedService;
import example.sim.BaseGameModule;
import example.sim.BaseGameService;
import example.sim.CommandListener;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.ini4j.Ini;

/**
 * This is the state that will load, instantiate, enable/disable, null and
 * remove states dynamically. Should hook into the chat in order to load/disable
 * mods on the fly
 *
 * @author Asser
 */
public class AdaptiveLoadingState extends AbstractHostedService implements CommandListener {

    //A map of settings (key,value) per class loaded
    private HashMap<Object, Ini> classSettings;
    // Create GroovyClassLoader.
    AdaptiveClassLoader classLoader;
    //final GroovyClassLoader classLoader = new GroovyClassLoader();
    private String[] directories;
    private final Vector<File> repository;
    
    private HashSet<BaseGameModule> modules;
    private HashSet<BaseGameService> services;
    
    private Pattern modulePattern = Pattern.compile("\\~startModule\\s(\\w+)");
    private Pattern servicePattern = Pattern.compile("\\~startService\\s(\\w+)");
    private Matcher m;

    //Used in distribution
    private String modLocation = "modules\\modules.jar";
    //Used from SDK
    private String modLocation2 = "build\\modules\\libs\\modules.jar";
    
    private GameSystemManager gameSystems;
    
    public AdaptiveLoadingState(GameSystemManager gameSystems) {
        repository = new Vector<>();
        classSettings = new HashMap<>();
        
        modules = new HashSet<>();
        services = new HashSet<>();

        //this.getManager();
        //TODO: Register with ChatHostedService as Pattern Listener
        this.gameSystems = gameSystems;
    }
    
    @Override
    protected void onInitialize(HostedServiceManager serviceManager) {
        
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
        
        this.getService(ChatHostedService.class).registerPatternListener(this, modulePattern);
        this.getService(ChatHostedService.class).registerPatternListener(this, servicePattern);
    }
    
    private void load(String className) throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        Ini ini = loadSettings("arena1/arena1.ini");
        loadClass("arena1.arena1", ini);
    }
    
    private void loadClass(String file, Ini settingsFile) throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        Class java = classLoader.loadClass(file);
        Constructor c = java.getConstructor(Ini.class);
        
        if (BaseGameModule.class.isAssignableFrom(java)) {
            BaseGameModule javaObj = (BaseGameModule) c.newInstance(settingsFile);
            boolean add = modules.add(javaObj);
            classSettings.put(javaObj, settingsFile);
            
        } else if (BaseGameService.class.isAssignableFrom(java)) {
            
            BaseGameService javaObj = (BaseGameService) c.newInstance(settingsFile);
            boolean add = services.add(javaObj);
            classSettings.put(javaObj, settingsFile);
        }
        
    }

    //Loads the associated settings ini-file
    private Ini loadSettings(String className) throws IOException {
        InputStream inputStream = classLoader.getResourceAsStream(className);
        Ini ini = new Ini(inputStream);
        return ini;
    }
    
    @Override
    public void start() {
    }
    
    @Override
    public void stop() {
    }
    
    @Override
    public Pattern getCommandPattern() {
        
        return modulePattern;
    }
    
    @Override
    public void interpretCommandGroup(String group) {
        //m = p.matcher(group);
        
        //If command following !startModule
        modules.stream().filter((module) -> (module.getClass().getSimpleName() == null ? group == null : module.getClass().getSimpleName().equals(group))).forEachOrdered((module) -> {
            gameSystems.addSystem(module);
        });
        
        services.stream().filter((service) -> (service.getClass().getSimpleName() == null ? group == null : service.getClass().getSimpleName().equals(group))).forEachOrdered((service) -> {
            this.getServiceManager().addService(service);
        });
    }
}
