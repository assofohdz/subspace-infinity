/*
 * $Id$
 * 
 * Copyright (c) 2016, Simsilica, LLC
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

package example;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.*;
import com.jme3.scene.*;

import com.simsilica.lemur.*;
import com.simsilica.lemur.component.SpringGridLayout;
import com.simsilica.lemur.style.ElementId;

/**
 *
 *
 *  @author    Paul Speed
 */
public class MainMenuState extends BaseAppState {

    private Container mainWindow;
    
    // For joining a server
    private TextField connectHost;
    private TextField connectPort;

    // For hosting a server
    private TextField hostPort;
    private TextField hostDescription;

    public MainMenuState() {
    }
    
    @Override   
    protected void initialize( Application app ) {
        mainWindow = new Container();
 
        Label title = mainWindow.addChild(new Label("SimEthereal Example"));
        title.setFontSize(32);
        title.setInsets(new Insets3f(10, 10, 0, 10));
        
        Container props;
        
        Container joinPanel = mainWindow.addChild(new Container());
        joinPanel.setInsets(new Insets3f(10, 10, 10, 10));
        joinPanel.addChild(new Label("Join a Network Server", new ElementId("title")));
        
        props = joinPanel.addChild(new Container(new SpringGridLayout(Axis.Y, Axis.X, FillMode.None, FillMode.Last)));
        props.setBackground(null);
        props.addChild(new Label("Connect to host:"));
        connectHost = props.addChild(new TextField("localhost"), 1);
        props.addChild(new Label("On port:"));
        connectPort = props.addChild(new TextField("4269"), 1);
        joinPanel.addChild(new Button("Connect"));
 
        Container hostPanel = mainWindow.addChild(new Container());
        hostPanel.setInsets(new Insets3f(10, 10, 10, 10));
        hostPanel.addChild(new Label("Host a Game Server", new ElementId("title")));
                
        props = hostPanel.addChild(new Container(new SpringGridLayout(Axis.Y, Axis.X, FillMode.None, FillMode.Last)));
        props.setBackground(null);
        props.addChild(new Label("Host on port:"));
        hostPort = props.addChild(new TextField("4269"), 1);
        
        hostPanel.addChild(new Label("Server Description"));
        hostDescription = hostPanel.addChild(new TextField("This server is mine.\nThere are many like it\nbut this one is mine."));
        hostDescription.setSingleLine(false);
        hostPanel.addChild(new Button("Begin Hosting"));
         
           
        // Calculate a standard scale and position from the app's camera
        // height
        int height = app.getCamera().getHeight();        
        Vector3f pref = mainWindow.getPreferredSize().clone();
        
        float standardScale = (height / 720f);
        pref.multLocal(1.5f * standardScale);
 
        // With a slight bias toward the top        
        float y = height * 0.6f + pref.y * 0.5f;
                                     
        mainWindow.setLocalTranslation(100 * standardScale, y, 0);
        mainWindow.setLocalScale(1.5f * standardScale);
    }
 
    @Override   
    protected void cleanup( Application app ) {
    }
    
    @Override   
    protected void onEnable() {
        Node gui = ((Main)getApplication()).getGuiNode();
        gui.attachChild(mainWindow);
    }
    
    @Override   
    protected void onDisable() {
        mainWindow.removeFromParent();
    }
}
