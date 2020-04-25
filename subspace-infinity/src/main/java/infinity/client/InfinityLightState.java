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
package infinity.client;

import com.jme3.app.Application;
import com.jme3.light.PointLight;
import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.texture.FrameBuffer;
import com.jme3.app.state.BaseAppState;

import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import infinity.ConnectionState;
import infinity.Main;
import infinity.api.es.PointLightComponent;
import infinity.api.es.Position;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author Asser
 */
@Deprecated
public class InfinityLightState extends BaseAppState {

    public int width;
    public int height;

    private EntityData ed;
    private EntitySet pointLightEntities;
    private Node lightRoot;
    ;
    private int fragmentShader;
    private int shaderProgram;

    private HashMap<EntityId, PointLight> pointLightMap = new HashMap<>();
    private Application application;

    public ArrayList<Light> lights = new ArrayList<Light>();
    public ArrayList<Block> blocks = new ArrayList<Block>();

    @Override
    protected void initialize(Application app) {
        this.application = app;

        this.ed = getState(ConnectionState.class).getEntityData();
        this.pointLightEntities = ed.getEntities(PointLightComponent.class, Position.class);

        this.lightRoot = new Node();

    }

    @Override
    protected void cleanup(Application app) {
    }

    @Override
    protected void onEnable() {
        lightRoot = ((Main) getApplication()).getRootNode();

        InfinitySceneProcessor isp = new InfinitySceneProcessor();
        isp.initialize(application.getRenderManager(), application.getViewPort());
        setUpObjects();

        application.getViewPort().addProcessor(isp);
    }

    @Override
    protected void onDisable() {

    }

    @Override
    public void update(float tpf) {

        pointLightEntities.applyChanges();

        if (pointLightEntities.hasChanges()) {
            for (Entity e : pointLightEntities.getAddedEntities()) {
                this.createPointLight(e);
            }

            for (Entity e : pointLightEntities.getChangedEntities()) {
                this.updatePointLight(e);
            }

            for (Entity e : pointLightEntities.getRemovedEntities()) {
                this.removePointLight(e);
            }
        }

    }

    private void removePointLight(Entity e) {
        PointLightComponent lt = e.get(PointLightComponent.class);
        PointLight pl = pointLightMap.remove(e.getId());
        lightRoot.removeLight(pl);

    }

    private void updatePointLight(Entity e) {
        Position p = e.get(Position.class);
        Vec3d location = p.getLocation();

        //PointLightComponent lt = e.get(PointLightComponent.class);
        PointLight pl = pointLightMap.get(e.getId());
        pl.setPosition(location.toVector3f());
    }

    private void createPointLight(Entity e) {
        PointLightComponent plc = e.get(PointLightComponent.class);
        Position p = e.get(Position.class);

        PointLight pl = new PointLight(p.getLocation().toVector3f(), plc.getColor(), plc.getRadius());

        pointLightMap.put(e.getId(), pl);

        lightRoot.addLight(pl);
    }

    @Override
    public String getId() {
        return "InfinityLightState";
    }

    private class InfinitySceneProcessor implements SceneProcessor {

        @Override
        public void initialize(RenderManager rm, ViewPort vp) {

            /*
            try {
                Display.setDisplayMode(new DisplayMode(width, height));
                Display.setTitle("2D Lighting");
                Display.create(new PixelFormat(0, 16, 1));
            } catch (LWJGLException e) {
                e.printStackTrace();
            }
             */
            shaderProgram = glCreateProgram();
            fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
            StringBuilder fragmentShaderSource = new StringBuilder();

            try {
                String line;
                BufferedReader reader = new BufferedReader(new FileReader("assets/Shaders/shader.frag"));
                while ((line = reader.readLine()) != null) {
                    fragmentShaderSource.append(line).append("\n");
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            glShaderSource(fragmentShader, fragmentShaderSource);
            glCompileShader(fragmentShader);
            if (glGetShaderi(fragmentShader, GL_COMPILE_STATUS) == GL_FALSE) {
                System.err.println("Fragment shader not compiled!");
            }

            glAttachShader(shaderProgram, fragmentShader);
            glLinkProgram(shaderProgram);
            glValidateProgram(shaderProgram);

            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();

            //New -->
            height = application.getCamera().getHeight();
            width = application.getCamera().getWidth();
            //<--

            glOrtho(0, width, height, 0, 1, -1);
            glMatrixMode(GL_MODELVIEW);

            glEnable(GL_STENCIL_TEST);
            glClearColor(0, 0, 0, 0);
        }

        @Override
        public void reshape(ViewPort vp, int w, int h) {
            height = h;
            width = w;
            glOrtho(0, width, height, 0, 1, -1);

        }

        @Override
        public boolean isInitialized() {
            return true;
        }

        @Override
        public void preFrame(float tpf) {
        }

        @Override
        public void postQueue(RenderQueue rq) {

        }

        @Override
        public void postFrame(FrameBuffer out) {
            render();
        }

        @Override
        public void cleanup() {
            glDeleteShader(fragmentShader);
            glDeleteProgram(shaderProgram);
        }

        @Override
        public void setProfiler(AppProfiler profiler) {

        }

    }

    private void render() {
        //glClear(GL_COLOR_BUFFER_BIT);
        for (Light light : lights) {
            glColorMask(false, false, false, false);
            glStencilFunc(GL_ALWAYS, 1, 1);
            glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);

            for (Block block : blocks) {
                Vector2f[] vertices = block.getVertices();
                for (int i = 0; i < vertices.length; i++) {
                    Vector2f currentVertex = vertices[i];
                    Vector2f nextVertex = vertices[(i + 1) % vertices.length];
                    Vector2f edge = nextVertex.subtract(currentVertex);
                    //Vector2f edge = Vector2f.sub(nextVertex, currentVertex, null);
                    Vector2f normal = new Vector2f(edge.getY(), -edge.getX());
                    Vector2f lightToCurrent = currentVertex.subtract(light.location);
                    //Vector2f lightToCurrent = Vector2f.sub(currentVertex, light.location, null);
                    if (normal.dot(lightToCurrent) > 0) {
                        //if (Vector2f.dot(normal, lightToCurrent) > 0) {
                        Vector2f point1 = currentVertex.add(currentVertex.subtract(light.location).mult(800));
                        //Vector2f point1 = Vector2f.add(currentVertex, (Vector2f) Vector2f.sub(currentVertex, light.location, null).scale(800), null);
                        Vector2f point2 = nextVertex.add(nextVertex.subtract(light.location).mult(800));
                        //Vector2f point2 = Vector2f.add(nextVertex, (Vector2f) Vector2f.sub(nextVertex, light.location, null).scale(800), null);
                        glBegin(GL_QUADS);
                        {
                            glVertex2f(currentVertex.getX(), currentVertex.getY());
                            glVertex2f(point1.getX(), point1.getY());
                            glVertex2f(point2.getX(), point2.getY());
                            glVertex2f(nextVertex.getX(), nextVertex.getY());
                        }
                        glEnd();
                    }
                }
            }

            glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
            glStencilFunc(GL_EQUAL, 0, 1);
            glColorMask(true, true, true, true);

            glUseProgram(shaderProgram);
            glUniform2f(glGetUniformLocation(shaderProgram, "lightLocation"), light.location.getX(), height - light.location.getY());
            glUniform3f(glGetUniformLocation(shaderProgram, "lightColor"), light.red, light.green, light.blue);
            glEnable(GL_BLEND);
            glBlendFunc(GL_ONE, GL_ONE);

            glBegin(GL_QUADS);
            {
                glVertex2f(0, 0);
                glVertex2f(0, height);
                glVertex2f(width, height);
                glVertex2f(width, 0);
            }
            glEnd();

            glDisable(GL_BLEND);
            glUseProgram(0);
            glClear(GL_STENCIL_BUFFER_BIT);
        }
        glColor3f(0, 0, 0);
        for (Block block : blocks) {
            glBegin(GL_QUADS);
            {
                for (Vector2f vertex : block.getVertices()) {
                    glVertex2f(vertex.getX(), vertex.getY());
                }
            }
            glEnd();
        }
        //Display.update();
        //Display.sync(60);
    }

    class Light {

        public Vector2f location;
        public float red;
        public float green;
        public float blue;

        public Light(Vector2f location, float red, float green, float blue) {
            this.location = location;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }

    class Block {

        public int x, y, localWidth, localHeight;

        public Block(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.localWidth = width;
            this.localHeight = height;
        }

        public Vector2f[] getVertices() {
            return new Vector2f[]{
                new Vector2f(x, y),
                new Vector2f(x, y + localHeight),
                new Vector2f(x + localWidth, y + localHeight),
                new Vector2f(x + localWidth, y)
            };
        }
    }

    //Test method
    private void setUpObjects() {
        int lightCount = 5 + (int) (Math.random() * 1);
        int blockCount = 5 + (int) (Math.random() * 1);

        for (int i = 1; i <= lightCount; i++) {
            Vector2f location = new Vector2f((float) Math.random() * width, (float) Math.random() * height);
            lights.add(new Light(location, (float) Math.random() * 10, (float) Math.random() * 10, (float) Math.random() * 10));
        }

        for (int i = 1; i <= blockCount; i++) {
            int localWidth = 50;
            int localHeight = 50;
            int x = (int) (Math.random() * (this.width - localWidth));
            int y = (int) (Math.random() * (this.height - localHeight));
            blocks.add(new Block(x, y, localWidth, localHeight));
        }
    }
}
