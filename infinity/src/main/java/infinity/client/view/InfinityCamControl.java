package infinity.client.view;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Copyright (c) 2009-2012 jMonkeyEngine
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.control.AbstractControl;
import com.jme3.util.TempVars;

import com.simsilica.mathd.Vec3i;
import org.slf4j.LoggerFactory;

/**
 * This Control maintains a reference to a Camera, which will be synched with
 * the position (worldTranslation) of the current spatial.
 *
 * @author tim
 */
public class InfinityCamControl extends AbstractControl {

    static org.slf4j.Logger log = LoggerFactory.getLogger(InfinityCamControl.class);

    private float distanceToCam = 31;

    public enum ControlDirection {

        /**
         * Means, that the Camera's transform is "copied" to the Transform of the
         * Spatial.
         */
        CameraToSpatial,
        /**
         * Means, that the Spatial's transform is "copied" to the Transform of the
         * Camera.
         */
        SpatialToCamera;
    }

    private Camera camera;
    private ControlDirection controlDir = ControlDirection.SpatialToCamera;

    /**
     * Constructor used for Serialization.
     */
    public InfinityCamControl(final float distanceToCam) {
        this.distanceToCam = distanceToCam;
    }

    /**
     * @param camera The Camera to be synced.
     */
    public InfinityCamControl(final Camera camera, @SuppressWarnings("unused") final float distanceToCam) {
        this.camera = camera;
    }

    /**
     * @param camera The Camera to be synced.
     */
    public InfinityCamControl(final Camera camera, final ControlDirection controlDir, final float distanceToCam) {
        this.camera = camera;
        this.controlDir = controlDir;
        this.distanceToCam = distanceToCam;
    }

    public Camera getCamera() {
        return camera;
    }

    public void setCamera(final Camera camera) {
        this.camera = camera;
    }

    public ControlDirection getControlDir() {
        return controlDir;
    }

    public void setControlDir(final ControlDirection controlDir) {
        this.controlDir = controlDir;
    }

    // fields used, when inversing ControlDirection:
    @Override
    protected void controlUpdate(final float tpf) {
        if (spatial != null && camera != null) {
            switch (controlDir) {
            case SpatialToCamera:
                final Vector3f spatialLoc = new Vector3f(spatial.getWorldTranslation());
                final Vector3f camLoc = new Vector3f(spatialLoc);
                camLoc.addLocal(0, distanceToCam, 0);

                camera.setLocation(camLoc);
                if (camLoc.x != 20){
                    //   log.info("controlUpdate:: Setting camera location to :"+camLoc);
                }
                if (camLoc.x == 20){
                    //    log.info("controlUpdate:: Setting camera location to :"+camLoc);
                }
                // camera.setRotation(spatial.getWorldRotation());
                camera.lookAt(spatialLoc, Vector3f.UNIT_Y);
                break;
            case CameraToSpatial:
                // set the localtransform, so that the worldtransform would be equal to the
                // camera's transform.
                // Location:
                final TempVars vars = TempVars.get();

                final Vector3f vecDiff = vars.vect1.set(camera.getLocation())
                        .subtractLocal(spatial.getWorldTranslation());
                spatial.setLocalTranslation(vecDiff.addLocal(spatial.getLocalTranslation()));

                // Rotation:
                final Quaternion worldDiff = vars.quat1.set(camera.getRotation())
                        .subtractLocal(spatial.getWorldRotation());
                spatial.setLocalRotation(worldDiff.addLocal(spatial.getLocalRotation()));
                vars.release();
                break;
            default:
                break;
            }
        }
    }

    @Override
    protected void controlRender(final RenderManager rm, final ViewPort vp) {
        // nothing to do
    }

    // default implementation from AbstractControl is equivalent
    // @Override
    // public Control cloneForSpatial(Spatial newSpatial) {
    // CameraControl control = new CameraControl(camera, controlDir);
    // control.setSpatial(newSpatial);
    // control.setEnabled(isEnabled());
    // return control;
    // }
    private static final String CONTROL_DIR_NAME = "controlDir";
    private static final String CAMERA_NAME = "camera";

    @Override
    public void read(final JmeImporter im) throws IOException {
        super.read(im);
        final InputCapsule ic = im.getCapsule(this);
        controlDir = ic.readEnum(CONTROL_DIR_NAME, ControlDirection.class, ControlDirection.SpatialToCamera);
        camera = (Camera) ic.readSavable(CAMERA_NAME, null);
    }

    @Override
    public void write(final JmeExporter ex) throws IOException {
        super.write(ex);
        final OutputCapsule oc = ex.getCapsule(this);
        oc.write(controlDir, CONTROL_DIR_NAME, ControlDirection.SpatialToCamera);
        oc.write(camera, CAMERA_NAME, null);
    }
}