/*
 * Copyright (c) 2018, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package infinity.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import org.ini4j.Ini;

import com.jme3.network.service.AbstractHostedService;
import com.jme3.network.service.HostedServiceManager;

import com.simsilica.es.EntityId;
import com.simsilica.sim.GameSystemManager;

import infinity.server.AccountHostedService;
import infinity.server.chat.ChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.AccountManager;
import infinity.sim.AdaptiveLoader;
import infinity.sim.ArenaManager;
import infinity.sim.BaseGameModule;
import infinity.sim.BaseGameService;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandConsumer;
import infinity.sim.InfinityPhysicsManager;
import infinity.sim.PhysicsManager;
import infinity.sim.TimeManager;
import infinity.systems.ArenaSystem;
import infinity.systems.InfinityTimeSystem;

/**
 * This is the state that will load, instantiate, enable/disable, null and
 * remove states dynamically. Should hook into the chat in order to load/disable
 * mods on the fly
 *
 * @author Asser
 */
public class AdaptiveLoadingService extends AbstractHostedService
        implements AdaptiveLoader /* implements CommandListener */ {

    // Create GroovyClassLoader.
    AdaptiveClassLoader classLoader;
    // final GroovyClassLoader classLoader = new GroovyClassLoader();
    // private String[] directories;
    private final Vector<File> repository;

    private final HashMap<String, BaseGameModule> modules;
    private final HashMap<String, BaseGameService> services;

    private final Pattern startModulePattern = Pattern.compile("\\~startModule\\s(\\w+)");
    private final Pattern startServicePattern = Pattern.compile("\\~startService\\s(\\w+)");
    private final Pattern stopModulePattern = Pattern.compile("\\~stopModule\\s(\\w+)");
    private final Pattern stopServicePattern = Pattern.compile("\\~stopService\\s(\\w+)");
    // private Matcher m;

    List<String> repositoryList = Arrays.asList(
            // Loading extensions:
            // Used in distribution
            "..\\modules\\modules-1.0.0-SNAPSHOT.jar",
            "..\\modules\\build\\libs\\modules-1.0.0-SNAPSHOT.jar",
            // Used from SDK
            "..\\build\\modules\\libs\\modules-1.0.0-SNAPSHOT.jar",
            // Extras
            "..\\modules");

    private final GameSystemManager gameSystems;

    public AdaptiveLoadingService(final GameSystemManager gameSystems) {
        repository = new Vector<>();
        // classSettings = new HashMap<>();

        modules = new HashMap<>();
        services = new HashMap<>();

        // this.getManager();
        // TODO: Register with ChatHostedService as Pattern Listener
        this.gameSystems = gameSystems;
    }

    /**
     * Initializes the file locations and registers with the ChatHostedService
     *
     * @param serviceManager the hosted service manager parent
     */
    @Override
    protected void onInitialize(final HostedServiceManager serviceManager) {

        /*
         * File file = new File("modules"); directories = file.list((File current,
         * String name) -> new File(current, name).exists());
         */
        final Consumer<String> consumerDirectories = folder -> {
            final File fileFolder = new File(folder);

            if (fileFolder.exists()) {

                String canonPath = null;
                try {
                    canonPath = fileFolder.getCanonicalPath();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                File canonPahFile = new File(canonPath);

                repository.add(canonPahFile);
            }
        };

        // Arrays.asList(directories).forEach(consumerDirectories);
        repositoryList.forEach(consumerDirectories);

        classLoader = new AdaptiveClassLoader(repository);
        // this.classLoader = new AdaptiveClassLoader(directories);

        // Register consuming methods for patterns
        this.getService(ChatHostedService.class).registerPatternBiConsumer(startModulePattern,
                "The command to start a new module is ~startModule <module>, where <module> is the module you want to start",
                new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id1, module1) -> startModule(id1, module1)));
        this.getService(ChatHostedService.class).registerPatternBiConsumer(stopModulePattern,
                "The command to start a new module is ~stopModule <module>, where <module> is the module you want to stop",
                new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, module) -> stopModule(id, module)));
        this.getService(ChatHostedService.class).registerPatternBiConsumer(startServicePattern,
                "The command to start a new module is ~startService <service>, where <service> is the service you want to start",
                new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, service) -> startService(id, service)));
        this.getService(ChatHostedService.class).registerPatternBiConsumer(stopServicePattern,
                "The command to start a new module is ~stopService <service>, where <service> is the service you want to stop",
                new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, service) -> stopService(id, service)));
    }

    /**
     * Loads an INI file and a class
     *
     * @param moduleName the name of the .class and .ini file to load
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     */
    private void loadModule(final String moduleName) throws IllegalAccessException, InstantiationException, IOException,
            ClassNotFoundException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        // Class prepended with their package name

        final String packageName = "infinity.modules."+moduleName;
        //final String clazz = packageName + "." + moduleName+"."+moduleName; //every module class is inside a package of their own name

        Class<?> javaGuava = getClassGuava(packageName, moduleName);
        if (javaGuava != null) {
            loadClass(javaGuava);
            return;
        }

        Class<?> java = getClass(packageName);
        if (java != null){
            loadClass(javaGuava);
            return;
        }
    }


    private Class<?> getClassGuava(String packageName, String className){

        final ClassLoader loader = Thread.currentThread().getContextClassLoader();

        try {
            ImmutableSet<ClassPath.ClassInfo> topLevelClasses = ClassPath.from(loader).getTopLevelClasses();
            for (final ClassPath.ClassInfo info : topLevelClasses) {
                if (info.getPackageName().equals(packageName) && info.getSimpleName().equals(className)) {
                    System.out.println("PackageName: "+packageName+", className:"+className);
                    System.out.println("info.getSimpleName(): "+info.getSimpleName());
                    System.out.println("info.getName(): "+info.getName());
                    System.out.println("info.getPackageName(): "+info.getPackageName());
                    Class<?> clazz = info.load();
                    System.out.println("Found class: "+clazz.toString());
                    return clazz;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Loads the class
    private Class<?> getClass(final String file) throws ClassNotFoundException {
        return classLoader.loadClass(file);
    }


    /**
     * Loads the .class file
     *
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     */
    // Loads the class
    private void loadClass(Class<?> java) throws IllegalAccessException, InstantiationException, IOException,
            ClassNotFoundException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        //final Class<?> java = classLoader.loadClass(file);
        final Constructor<?> c = java.getConstructor(ChatHostedPoster.class, AccountManager.class, AdaptiveLoader.class,
                ArenaManager.class, TimeManager.class, PhysicsManager.class);

        if (BaseGameModule.class.isAssignableFrom(java)) {
            final BaseGameModule javaObj = (BaseGameModule) c.newInstance(getService(ChatHostedService.class),
                    getService(AccountHostedService.class), this, gameSystems.get(ArenaSystem.class),
                    gameSystems.get(InfinityTimeSystem.class), gameSystems.get(InfinityPhysicsManager.class));
            modules.put(javaObj.getClass().getSimpleName(), javaObj);
            // classSettings.put(javaObj, settingsFile);

        } else if (BaseGameService.class.isAssignableFrom(java)) {

            final BaseGameService javaObj = (BaseGameService) c.newInstance(getService(ChatHostedService.class),
                    getService(AccountHostedService.class), this, gameSystems.get(ArenaSystem.class),
                    gameSystems.get(InfinityTimeSystem.class), gameSystems.get(InfinityPhysicsManager.class));
            services.put(javaObj.getClass().getSimpleName(), javaObj);
            // classSettings.put(javaObj, settingsFile);
        }

    }

    /**
     *
     * TiME MANAGER +++++ PHYSICS MANAGER!!!
     *
     *
     *
     */
    @Override
    public void start() {
        return;
    }

    @Override
    public void stop() {
        return;
    }

    /**
     * Starts the given module. Is called when the "~startModule <module>" command
     * is given
     *
     * @param module the module to start
     * @param id     caller of the command
     */
    private void startModule(final EntityId id, final String module) {
        BaseGameModule bgm;
        if (!modules.containsKey(module)) {
            try {
                // Try to load it
                loadModule(module);
            } catch (final IllegalAccessException ex) {
                Logger.getLogger(AdaptiveLoadingService.class.getName()).log(Level.SEVERE, null, ex);
                return;
            } catch (final InstantiationException ex) {
                Logger.getLogger(AdaptiveLoadingService.class.getName()).log(Level.SEVERE, null, ex);
                return;
            } catch (final IOException ex) {
                Logger.getLogger(AdaptiveLoadingService.class.getName()).log(Level.SEVERE, null, ex);
                return;
            } catch (final ClassNotFoundException ex) {
                Logger.getLogger(AdaptiveLoadingService.class.getName()).log(Level.SEVERE, null, ex);
                return;
            } catch (final NoSuchMethodException ex) {
                Logger.getLogger(AdaptiveLoadingService.class.getName()).log(Level.SEVERE, null, ex);
                return;
            } catch (final IllegalArgumentException ex) {
                Logger.getLogger(AdaptiveLoadingService.class.getName()).log(Level.SEVERE, null, ex);
                return;
            } catch (final InvocationTargetException ex) {
                Logger.getLogger(AdaptiveLoadingService.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }

            bgm = modules.get(module);

        } else {
            bgm = modules.get(module);

        }

        gameSystems.addSystem(bgm);

        /*
         * if (bgm instanceof CommandListener) { CommandListener cl = (CommandListener)
         * bgm; HashMap<Pattern, CommandConsumer> map = cl.getPatternBiConsumers(); for
         * (Pattern p : map.keySet()) {
         * this.getService(ChatHostedService.class).registerPatternBiConsumer(p,
         * map.get(p)); } }
         */
    }

    /**
     * Stop the given module. Is called when the "~stopModule <module>" command is
     * given
     *
     * @param module the module to stop
     * @param id     caller of the command
     */
    private void stopModule(final EntityId id, final String module) {
        gameSystems.removeSystem(modules.get(module));
    }

    /**
     * Starts the given service. Is called when the "~startService <service>"
     * command is given
     *
     * @param service the service to start
     * @param id      caller of the command
     */
    private void startService(final EntityId id, final String service) {
        getServiceManager().addService(services.get(service));
    }

    /**
     * Stops the given service. Is called when the "~stopService <service>" command
     * is given
     *
     * @param service the service to stop
     * @param id      caller of the command
     */
    private void stopService(final EntityId id, final String service) {
        getServiceManager().removeService(services.get(service));
    }

    /**
     * Will validate the settings against core mechanics. The template.sss file is
     * used to validate settings
     *
     * @param settings
     * @return true if settings are ok, false if not
     * @throws IOException if file io goes wrong
     */
    @Override
    public boolean validateSettings(final Ini settings) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }
}
