package infinity.client.view;

//USAGE :
/*
testMaterial = new Material(assetManager,"Common/MatDefs/Light/Lighting.j3md");
  
PaintableTexture paint = new PaintableTexture(16,16);
paint.setBackground(new Color(0,0,0,0));
paint.setPixel(10, 10, new Color(0,0,255,255));
  
testMaterial.setTexture("m_DiffuseMap", paint.getTexture());
  
testMaterial.setFloat("m_Shininess", 128f); // [0,128]
testMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
  
item.setMaterial(testMaterial);
 */
//---------------------------------------------------------------------------
// PaintableTexture class
//---------------------------------------------------------------------------

import com.jme3.math.ColorRGBA;
import java.awt.Color;
import java.nio.ByteBuffer;

import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture.MagFilter;
import com.jme3.texture.Texture2D;
import com.jme3.util.BufferUtils;

public class PaintableTexture {

    protected byte[] data;
    protected Image image   ;
    protected Texture2D texture;
    int width;
    int height;

    public PaintableTexture(int width, int height) {

        this.width = width;
        this.height = height;

        // create black image
        data = new byte[width * height * 4];
        setBackground(new Color(0, 0, 0, 255));

        // set data to texture
        ByteBuffer buffer = BufferUtils.createByteBuffer(data);
        image = new Image(Format.RGBA8, width, height, buffer);
        texture = new Texture2D(image);
        texture.setMagFilter(Texture.MagFilter.Nearest);

    }

    public Texture getTexture() {
        ByteBuffer buffer = BufferUtils.createByteBuffer(data);
        image.setData(buffer);
        return texture;
    }
/*
    public void setPixel(int x, int y, ColorRGBA color) {
        int i = (x + y * width) * 4;
        data[i] = (byte) color.r; // r
        data[i + 1] = (byte) color.g; // g
        data[i + 2] = (byte) color.b; // b
        data[i + 3] = (byte) color.a; // a
    }
*/
    public void setPixel(int x, int y, Color color) {
        int i = (x + y * width) * 4;
        data[i] = (byte) color.getRed(); // r
        data[i + 1] = (byte) color.getGreen(); // g
        data[i + 2] = (byte) color.getBlue(); // b
        data[i + 3] = (byte) color.getAlpha(); // a
    }

    public void setBackground(Color color) {

        for (int i = 0; i < width * height * 4; i += 4) {
            data[i] = (byte) color.getRed(); // r
            data[i + 1] = (byte) color.getGreen(); // g
            data[i + 2] = (byte) color.getBlue(); // b
            data[i + 3] = (byte) color.getAlpha(); // a

        }
    }
  /*  
    public void setBackground(ColorRGBA color){
        
        for (int i = 0; i < width * height * 4; i += 4) {
            data[i] = (byte) color.r; // r
            data[i + 1] = (byte) color.g; // g
            data[i + 2] = (byte) color.b; // b
            data[i + 3] = (byte) color.a; // a

        }
    }
*/
    public void setMagFilter(MagFilter filter) {
        texture.setMagFilter(filter);
    }

}
