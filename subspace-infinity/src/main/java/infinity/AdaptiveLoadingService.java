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
package infinity;

import infinity.api.sim.AdaptiveLoader;
import com.jme3.network.service.AbstractHostedService;
import com.jme3.network.service.HostedServiceManager;
import com.simsilica.es.EntityId;
import com.simsilica.sim.GameSystemManager;
import infinity.net.chat.server.ChatHostedService;
import infinity.net.server.AccountHostedService;
import infinity.api.sim.AccessLevel;
import infinity.api.sim.AccountManager;
import infinity.api.sim.ArenaManager;
import infinity.api.sim.BaseGameModule;
import infinity.api.sim.BaseGameService;
import infinity.api.sim.ChatHostedPoster;
import infinity.api.sim.CommandConsumer;
import infinity.es.states.arena.ArenaState;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
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
public class AdaptiveLoadingService extends AbstractHostedService implements AdaptiveLoader /*implements CommandListener*/ {

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

    List<String> repositoryList = Arrays.asList(
            //Loading extensions:
            //Used in distribution
            "modules\\modules.jar",
            //Used from SDK
            "build\\modules\\libs\\modules.jar",
            //Extras
            "modules"
    );

    private GameSystemManager gameSystems;

    public AdaptiveLoadingService(GameSystemManager gameSystems) {
        repository = new Vector<>();
        //classSettings = new HashMap<>();

        modules = new HashMap<>();
        services = new HashMap<>();

        //this.getManager();
        //TODO: Register with ChatHostedService as Pattern Listener
        this.gameSystems = gameSystems;
    }

    /**
     * Initializes the file locations and registers with the ChatHostedService
     *
     * @param serviceManager the hosted service manager parent
     */
    @Override
    protected void onInitialize(HostedServiceManager serviceManager) {

        /*
        File file = new File("modules");
        directories = file.list((File current, String name) -> new File(current, name).exists());
         */
        Consumer<String> consumerDirectories = folder -> {
            File fileFolder = new File(folder);
            if (fileFolder.exists()) {
                repository.add(fileFolder);
            }
        };

        //Arrays.asList(directories).forEach(consumerDirectories);
        repositoryList.forEach(consumerDirectories);

        this.classLoader = new AdaptiveClassLoader(repository);
        //this.classLoader = new AdaptiveClassLoader(directories);

        //Register consuming methods for patterns
        this.getService(ChatHostedService.class).registerPatternBiConsumer(startModulePattern, "The command to start a new module is ~startModule <module>, where <module> is the module you want to start", new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, module) -> this.startModule(id, module)));
        this.getService(ChatHostedService.class).registerPatternBiConsumer(stopModulePattern, "The command to start a new module is ~stopModule <module>, where <module> is the module you want to stop", new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, module) -> this.stopModule(id, module)));
        this.getService(ChatHostedService.class).registerPatternBiConsumer(startServicePattern, "The command to start a new module is ~startService <service>, where <service> is the service you want to start", new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, service) -> this.startService(id, service)));
        this.getService(ChatHostedService.class).registerPatternBiConsumer(stopServicePattern, "The command to start a new module is ~stopService <service>, where <service> is the service you want to stop", new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, service) -> this.stopService(id, service)));
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
    private void loadModule(String moduleName) throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        //Class prepended with their package name
        String clazz = moduleName + "." + moduleName;
        //Ini ini = loadSettings(settings);
        loadClass(clazz);
    }

    /**
     * Loads the .class file
     *
     * @param file the file to load
     * @param settingsFile the .ini-file to to load and use when instancing the
     * .class file
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     */
    //Loads the class
    private void loadClass(String file) throws IllegalAccessException, InstantiationException, IOException, ClassNotFoundException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        Class java = classLoader.loadClass(file);
        Constructor c = java.getConstructor(ChatHostedPoster.class, AccountManager.class, AdaptiveLoader.class, ArenaManager.class);

        if (BaseGameModule.class.isAssignableFrom(java)) {
            BaseGameModule javaObj = (BaseGameModule) c.newInstance((ChatHostedPoster) getService(ChatHostedService.class), (AccountManager) getService(AccountHostedService.class), (AdaptiveLoader) this, (ArenaManager) gameSystems.get(ArenaState.class));
            modules.put(javaObj.getClass().getSimpleName(), javaObj);
            //classSettings.put(javaObj, settingsFile);

        } else if (BaseGameService.class.isAssignableFrom(java)) {

            BaseGameService javaObj = (BaseGameService) c.newInstance(getService(ChatHostedService.class), getService(AccountHostedService.class), (AdaptiveLoader) this, (ArenaManager) gameSystems.get(ArenaState.class));
            services.put(javaObj.getClass().getSimpleName(), javaObj);
            //classSettings.put(javaObj, settingsFile);
        }

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
     * @param id caller of the command
     */
    private void startModule(EntityId id, String module) {
        BaseGameModule bgm;
        if (!modules.containsKey(module)) {
            try {
                //Try to load it
                this.loadModule(module);
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
                Logger.getLogger(AdaptiveLoadingService.class.getName()).log(Level.SEVERE, null, ex);
            }

            bgm = modules.get(module);

        } else {
            bgm = modules.get(module);

        }

        gameSystems.addSystem(bgm);

        /*
        if (bgm instanceof CommandListener) {
            CommandListener cl = (CommandListener) bgm;
            HashMap<Pattern, CommandConsumer> map = cl.getPatternBiConsumers();
            for (Pattern p : map.keySet()) {
                this.getService(ChatHostedService.class).registerPatternBiConsumer(p, map.get(p));
            }
        }*/
    }

    /**
     * Stop the given module. Is called when the "~stopModule <module>" command
     * is given
     *
     * @param module the module to stop
     * @param id caller of the command
     */
    private void stopModule(EntityId id, String module) {
        gameSystems.removeSystem(modules.get(module));
    }

    /**
     * Starts the given service. Is called when the "~startService <service>"
     * command is given
     *
     * @param service the service to start
     * @param id caller of the command
     */
    private void startService(EntityId id, String service) {
        getServiceManager().addService(services.get(service));
    }

    /**
     * Stops the given service. Is called when the "~stopService <service>"
     * command is given
     *
     * @param service the service to stop
     * @param id caller of the command
     */
    private void stopService(EntityId id, String service) {
        getServiceManager().removeService(services.get(service));
    }

    /**
     * Will validate the settings against core mechanics. The template.sss file
     * is used to validate settings
     *
     * @param settings
     * @return true if settings are ok, false if not
     * @throws IOException if file io goes wrong
     */
    @Override
    public boolean validateSettings(Ini settings) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Loads the .ini file with Ini4J
     *
     * @param iniFileName name of the file (without filetype)
     * @return the Ini object holding all settings inside the ini-file
     * @throws IOException
     */
    @Override
    public Ini loadSettings(String iniFileName) throws IOException {
        //Ini files are considered resources
        String settings = iniFileName + "/" + iniFileName + ".ini";
        InputStream inputStream = classLoader.getResourceAsStream(settings);
        Ini ini = new Ini(inputStream);
        return ini;
    }
}
