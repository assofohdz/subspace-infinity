/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example;

import com.jme3.network.service.AbstractHostedService;
import com.jme3.network.service.HostedServiceManager;
import com.simsilica.es.EntityId;
import com.simsilica.sim.GameSystemManager;
import example.net.chat.server.ChatHostedService;
import example.net.server.AccountHostedService;
import example.sim.AccountLevels;
import example.sim.BaseGameModule;
import example.sim.BaseGameService;
import example.sim.CommandConsumer;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
public class AdaptiveLoadingService extends AbstractHostedService /*implements CommandListener*/ {

    //A map of settings (key,value) per class loaded
    private HashMap<Object, Ini> classSettings;
    // Create GroovyClassLoader.
    AdaptiveClassLoader classLoader;
    //final GroovyClassLoader classLoader = new GroovyClassLoader();
    private String[] directories;
    private final Vector<File> repository;

    private HashMap<String, BaseGameModule> modules;
    private HashMap<String, BaseGameService> services;

    private final Pattern startModulePattern = Pattern.compile("\\~startModule\\s(\\w+)");
    private final Pattern startServicePattern = Pattern.compile("\\~startService\\s(\\w+)");
    private final Pattern stopModulePattern = Pattern.compile("\\~stopModule\\s(\\w+)");
    private final Pattern stopServicePattern = Pattern.compile("\\~stopService\\s(\\w+)");
    private Matcher m;

    //Used in distribution
    private String modLocation = "modules\\modules.jar";
    //Used from SDK
    private String modLocation2 = "build\\modules\\libs\\modules.jar";

    private GameSystemManager gameSystems;

    public AdaptiveLoadingService(GameSystemManager gameSystems) {
        repository = new Vector<>();
        classSettings = new HashMap<>();

        modules = new HashMap<>();
        services = new HashMap<>();

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
            Logger.getLogger(AdaptiveLoadingService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            Logger.getLogger(AdaptiveLoadingService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(AdaptiveLoadingService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(AdaptiveLoadingService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(AdaptiveLoadingService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(AdaptiveLoadingService.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            ex.getCause().printStackTrace();
            Logger.getLogger(AdaptiveLoadingService.class.getName()).log(Level.SEVERE, null, ex);
        }

        //Register consuming methods for patterns
        this.getService(ChatHostedService.class).registerPatternBiConsumer(startModulePattern, new CommandConsumer(AccountLevels.PLAYER_LEVEL, (id, module) -> this.startModule(id, module)));
        this.getService(ChatHostedService.class).registerPatternBiConsumer(stopModulePattern, new CommandConsumer(AccountLevels.PLAYER_LEVEL, (id, module) -> this.stopModule(id, module)));
        this.getService(ChatHostedService.class).registerPatternBiConsumer(startServicePattern, new CommandConsumer(AccountLevels.PLAYER_LEVEL, (id, service) -> this.startService(id, service)));
        this.getService(ChatHostedService.class).registerPatternBiConsumer(stopServicePattern, new CommandConsumer(AccountLevels.PLAYER_LEVEL, (id, service) -> this.stopService(id, service)));
    }

    //Loads an INI file and a class
    private void load(String className) throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        Ini ini = loadSettings("arena1/arena1.ini");
        loadClass("arena1.arena1", ini);
    }

    //Loads the class
    private void loadClass(String file, Ini settingsFile) throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        Class java = classLoader.loadClass(file);
        Constructor c = java.getConstructor(Ini.class);

        if (BaseGameModule.class.isAssignableFrom(java)) {
            BaseGameModule javaObj = (BaseGameModule) c.newInstance(settingsFile);
            modules.put(javaObj.getClass().getSimpleName(), javaObj);
            classSettings.put(javaObj, settingsFile);

        } else if (BaseGameService.class.isAssignableFrom(java)) {

            BaseGameService javaObj = (BaseGameService) c.newInstance(settingsFile);
            services.put(javaObj.getClass().getSimpleName(), javaObj);
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

    /**
     * Starts the given module. Is called when the "~startModule <module>"
     * command is given
     *
     * @param module the module to start
     */
    private void startModule(EntityId id, String module) {

        BaseGameModule bgm = modules.get(module);
        gameSystems.addSystem(bgm);
        if (bgm instanceof CommandListener) {
            CommandListener cl = (CommandListener) bgm;
            HashMap<Pattern, CommandConsumer> map = cl.getPatternBiConsumers();
            for (Pattern p : map.keySet()) {
                this.getService(ChatHostedService.class).registerPatternBiConsumer(p, map.get(p));
            }
        }
    }

    /**
     * Stop the given module. Is called when the "~stopModule <module>" command
     * is given
     *
     * @param module the module to stop
     */
    private void stopModule(EntityId id, String module) {
        gameSystems.removeSystem(modules.get(module));
    }

    /**
     * Starts the given service. Is called when the "~startService <service>"
     * command is given
     *
     * @param service the service to start
     */
    private void startService(EntityId id, String service) {
        getServiceManager().addService(services.get(service));
    }

    /**
     * Stops the given service. Is called when the "~stopService <service>"
     * command is given
     *
     * @param service the service to stop
     */
    private void stopService(EntityId id, String service) {
        getServiceManager().removeService(services.get(service));
    }
}
