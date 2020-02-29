/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package volvis;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import gui.RaycastRendererPanel;
import gui.TransferFunction2DEditor;
import gui.TransferFunctionEditor;
import util.TFChangeListener;
import util.TriInterpolation;
import util.VectorMath;
import volume.GradientVolume;
import volume.Volume;
import volume.VoxelGradient;

import java.awt.image.BufferedImage;

/**
 *
 * @author michel
 */
public class RaycastRenderer extends Renderer implements TFChangeListener {

    private Volume volume = null;
    private GradientVolume gradients = null;
    RaycastRendererPanel panel;
    TransferFunction tFunc;
    TransferFunctionEditor tfEditor;
    TransferFunction2DEditor tfEditor2D;



// ours

    final static int INTERACTIVE_MODE_STEP = 4;
    final static int INTERACTIVE_MODE_GRANULARITY = 2;
    final static int NON_INTERACTIVE_MODE_STEP = 1;
    final static int NON_INTERACTIVE_MODE_GRANULARITY = 1;

    int granularity = NON_INTERACTIVE_MODE_GRANULARITY;
    int step = NON_INTERACTIVE_MODE_STEP;

    private boolean shading;
    double ambient = 0.1;
    double diff = 0.7;
    double spec = 0.2;
    public void setAmbient(double a){
        ambient=a;
    }
    public void setDiff(double a){
        diff=a;
    }
    public void setSpec(double a){
        spec=a;
    }
    TFColor light = new TFColor(1, 1, 1, 1);
    double alp = 10;
    public void setShading(boolean s) {
        shading = s;
    }

    // OURS
    private boolean illuminate = false;

    public int imageSize;
    private int max;
    private BufferedImage image;
    private double[] viewMatrix = new double[4 * 4];

    public enum RENDER_METHOD {
        SLICES, MIP, COMPOSITING, TF2D
    }
    private RENDER_METHOD method;
    // END

//     ours    for switching function
    public static final int FUNCTION_MAXGRADIENT = 5;
    public static final int FUNCTION_2DFUNC = 4;
    public static final int FUNCTION_COMPOSITING = 3;
    public static final int FUNCTION_MIP = 2;
    public static final int FUNCTION_SLICER = 1;

    private int function;

    public void setFunction(int function) {
        this.function = function;
    }
    //end


    
    public RaycastRenderer() {
        method = RENDER_METHOD.SLICES;  // OURS
        panel = new RaycastRendererPanel(this);
        panel.setSpeedLabel("0");
    }

    // Ours
    @Override
    public void setInteractiveMode(boolean flag) {
        interactiveMode = flag;
    }

    public void setRenderMethod(RENDER_METHOD method) {
        this.method = method;
        changed();
    }

    public void toggleIlluminate() {
        illuminate = !illuminate;
        changed();
    }
    // end


    // Method to set the imageSize to the next power of 2
    private int powerofTwo (int value){
        int highestOneBit = Integer.highestOneBit(value);
        if (value == highestOneBit) {
            return value;
        }
        return highestOneBit << 1;

    }

    public void setVolume(Volume vol) {
        System.out.println("Assigning volume");
        volume = vol;

        System.out.println("Computing gradients");
        gradients = new GradientVolume(vol);

        // set up image for storing the resulting rendering
        // the image width and height are equal to the length of the volume diagonal
        imageSize = (int) Math.floor(Math.sqrt(vol.getDimX() * vol.getDimX() + vol.getDimY() * vol.getDimY()
                + vol.getDimZ() * vol.getDimZ()));
        imageSize = powerofTwo(imageSize);

        image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
        // create a standard TF where lowest intensity maps to black, the highest to white, and opacity increases
        // linearly from 0.0 to 1.0 over the intensity range
        tFunc = new TransferFunction(volume.getMinimum(), volume.getMaximum());
        
        // uncomment this to initialize the TF with good starting values for the orange dataset 
        //tFunc.setTestFunc();
        
        
        tFunc.addTFChangeListener(this);
        tfEditor = new TransferFunctionEditor(tFunc, volume.getHistogram());
        
        tfEditor2D = new TransferFunction2DEditor(volume, gradients);
        tfEditor2D.addTFChangeListener(this);

        System.out.println("Finished initialization of RaycastRenderer");


        max = volume.getMaximum(); //ours

    }

    public RaycastRendererPanel getPanel() {
        return panel;
    }

    public TransferFunction2DEditor getTF2DPanel() {
        return tfEditor2D;
    }
    
    public TransferFunctionEditor getTFPanel() {
        return tfEditor;
    }
     

    short getVoxel(double[] coord) {

        if (coord[0] < 0 || coord[0] > volume.getDimX() || coord[1] < 0 || coord[1] > volume.getDimY()
                || coord[2] < 0 || coord[2] > volume.getDimZ()) {
            return 0;
        }

        int x = (int) Math.floor(coord[0]);
        int y = (int) Math.floor(coord[1]);
        int z = (int) Math.floor(coord[2]);

        return volume.getVoxel(x, y, z);
    }


    //OURS

    void MIP(double[] viewMatrix) {
        // clear image
        for (int j = 0; j < imageSize; j++) {
            for (int i = 0; i < imageSize; i++) {
                image.setRGB(i, j, 0);
            }
        }

        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);

        // image is square
        int imageCenter = imageSize / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();

        int limit = (int) Math.sqrt(Math.pow(volume.getDimX() * viewVec[0], 2)
                + Math.pow(volume.getDimY() * viewVec[1], 2)
                + Math.pow(volume.getDimZ() * viewVec[2], 2));
        for (int j = 0; j < imageSize; j += granularity) {
            for (int i = 0; i < imageSize; i += granularity) {
                int val = 0;
                for (int loop = (int) (limit / 2); loop > -limit / 2; loop = loop - step) {
                    pixelCoord=getPixelCoord(uVec,vVec,viewVec,volumeCenter,imageCenter,loop,i,j);
                    val = Math.max(val, TriInterpolation.triInterVoxel(pixelCoord,volume));
                }

                voxelColor.r = val / max;
                voxelColor.g = voxelColor.r;
                voxelColor.b = voxelColor.r;
                voxelColor.a = val > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque

                setRGB2Image(voxelColor, i, j, granularity);
            }
        }
    }
    void compositing(double[] viewMatrix) {
        // clear image
        for (int j = 0; j < imageSize; j++) {
            for (int i = 0; i < imageSize; i++) {
                image.setRGB(i, j, 0);
            }
        }

        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);

        // image is square
        int imageCenter = imageSize / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data

        TFColor voxelColor;

        int limit = (int) Math.sqrt(Math.pow(volume.getDimX() * viewVec[0], 2)
                + Math.pow(volume.getDimY() * viewVec[1], 2)
                + Math.pow(volume.getDimZ() * viewVec[2], 2));
        for (int j = 0; j < imageSize; j += granularity) {
            for (int i = 0; i < imageSize; i += granularity) {
                voxelColor = new TFColor(0, 0, 0, 1);
                for (int loop_i = limit / 2; loop_i > -limit / 2; loop_i -= step) {
                    pixelCoord = getPixelCoord(uVec, vVec, viewVec, volumeCenter, imageCenter, loop_i, i, j);
                    voxelColor = calColor(tFunc.getColor(TriInterpolation.triInterVoxel(pixelCoord, volume)), voxelColor);
                }

                // Map the intensity to a grey value by linear scaling
                //TODO: map to another function
//                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
//                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
//                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
//                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
//                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
//                image.setRGB(i, j, pixelColor);


                setRGB2Image(voxelColor, i, j, granularity);
            }
        }
    }
    void setRGB2Image(TFColor voxelColor, int i, int j, int granularity) { //
//        BufferedImage expects a pixel color packed as ARGB in an int
        int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
        int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
        int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255)
                : 255;
        int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b
                * 255) : 255;
        int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green
                << 8) | c_blue;
        for (int m = 0; m < granularity; m++) {
            for (int n = 0; n < granularity; n++) {
                if (n + i >= imageSize || n + i
                        < 0
                        || m + j >= imageSize || m + j < 0) {
                } else {
                    image.setRGB(n + i, m + j,
                            pixelColor);
                }
            }
        }

    }

    TFColor calColor(TFColor val, TFColor old) {
        TFColor result = new TFColor();
        result.a = old.a * (1 - val.a) + val.a;
        result.r = old.r * (1 - val.a) + val.a * val.r;
        result.g = old.g * (1 - val.a) + val.a * val.g;
        result.b = old.b * (1 - val.a) + val.a * val.b;
        return result;
    }

    double[] getPixelCoord(double[] uVec, double[] vVec, double[] viewVec, double[] volumeCenter, int imageCenter, int loop, int i, int j) {
        double[] pixelCoord = new double[3];
        pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                + volumeCenter[0] + loop * viewVec[0];
        pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                + volumeCenter[1] + loop * viewVec[1];
        pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                + volumeCenter[2] + loop * viewVec[2];
        return pixelCoord;
    }



    //END



    //Ours
    void twoDimFunction2(double[] viewMatrix) {
        TransferFunction2DEditor.TriangleWidget triWidget = this.getTF2DPanel().triangleWidget;
        double r = triWidget.radius;
        short fv = triWidget.baseIntensity;
        TFColor widgetColor = triWidget.color;

        double graMax = triWidget.maxGradient;
        double graMin = triWidget.minGradient;

//        System.out.println(r+","+fv+","+widgetColor+","+graMax+","+graMin);

        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        // image is square
        int imageCenter = imageSize / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        int limit = (int) Math.sqrt(Math.pow(volume.getDimX() * viewVec[0], 2)
                + Math.pow(volume.getDimY() * viewVec[1], 2)
                + Math.pow(volume.getDimZ() * viewVec[2], 2));
        for (int j = 0; j < imageSize; j += granularity) {
            for (int i = 0; i < imageSize; i += granularity) {
                image.setRGB(i, j, 0);
                TFColor voxelColor = new TFColor(0, 0, 0, 1);
                for (int loop_i = limit / 2; loop_i > -limit / 2; loop_i -= step) {
//                for (int loop_i = -limit / 2; loop_i < limit / 2; loop_i += step) {
                    pixelCoord = getPixelCoord(uVec, vVec, viewVec, volumeCenter, imageCenter, loop_i, i, j);
                    VoxelGradient gra = TriInterGradient(pixelCoord, volume);
                    if (gra.mag > graMax || gra.mag < graMin) {
                        continue;
                    }

                    //which one we should use? todo

                    voxelColor = cal2dColor2(voxelColor, widgetColor, TriInterpolation.triInterVoxel(pixelCoord, volume), fv, r, gra, viewVec);
//                    voxelColor = calColor(tFunc.getColor(TriInterpolation.triInterVoxel(pixelCoord, volume)), voxelColor);
                }
//                voxelColor.a = 1 - voxelColor.a;
                setRGB2Image(voxelColor, i, j, granularity);
            }
        }
    }



    // put the following 2 methods in out TriInterpolation class and also use the other implementation of Interpolation todo
    private double getLinearInterpolation(double d1, double d2, int x1, int x2, double x) {
        return d1 + (x - x1) * (d1 - d2) / (x1 - x2);
    }

    private VoxelGradient TriInterGradient(double[] coord, Volume volume) {
        double xd = coord[0];
        double yd = coord[1];
        double zd = coord[2];

        int x = (int) Math.floor(xd);
        int y = (int) Math.floor(yd);
        int z = (int) Math.floor(zd);
        int xc = (int) Math.ceil(xd);
        int yc = (int) Math.ceil(yd);
        int zc = (int) Math.ceil(zd);

        if (x < 0 || x > volume.getDimX() || y < 0 || y > volume.getDimY()
                || z < 0 || z > volume.getDimZ() || xc < 0
                || xc > volume.getDimX() || yc < 0 || yc > volume.getDimY()
                || zc < 0 || zc > volume.getDimZ()) {
            return new VoxelGradient();
        }
//        return
        VoxelGradient xyz = gradients.getVoxel(x, y, z);
        VoxelGradient xcyz = gradients.getVoxel(xc, y, z);
        VoxelGradient xycz = gradients.getVoxel(x, yc, z);
        VoxelGradient xyzc = gradients.getVoxel(x, y, zc);
        VoxelGradient xcycz = gradients.getVoxel(xc, yc, z);
        VoxelGradient xyczc = gradients.getVoxel(x, yc, zc);
        VoxelGradient xcyzc = gradients.getVoxel(xc, y, zc);
        VoxelGradient xcyczc = gradients.getVoxel(xc, yc, zc);

        return new VoxelGradient((float) getLinearInterpolation(
                getLinearInterpolation(
                        getLinearInterpolation(xyz.x, xcyz.x, x, xc, xd),
                        getLinearInterpolation(xycz.x, xcycz.x, x, xc, xd), y, yc, yd),
                getLinearInterpolation(
                        getLinearInterpolation(xyzc.x, xcyzc.x, x, xc, xd),
                        getLinearInterpolation(xyczc.x, xcyczc.x, x, xc, xd), y, yc, yd), z, zc, zd),
                (float) getLinearInterpolation(
                        getLinearInterpolation(
                                getLinearInterpolation(xyz.y, xcyz.y, x, xc, xd),
                                getLinearInterpolation(xycz.y, xcycz.y, x, xc, xd), y, yc, yd),
                        getLinearInterpolation(
                                getLinearInterpolation(xyzc.y, xcyzc.y, x, xc, xd),
                                getLinearInterpolation(xyczc.y, xcyczc.y, x, xc, xd), y, yc, yd), z, zc, zd),
                (float) getLinearInterpolation(
                        getLinearInterpolation(
                                getLinearInterpolation(xyz.z, xcyz.z, x, xc, xd),
                                getLinearInterpolation(xycz.z, xcycz.z, x, xc, xd), y, yc, yd),
                        getLinearInterpolation(
                                getLinearInterpolation(xyzc.z, xcyzc.z, x, xc, xd),
                                getLinearInterpolation(xyczc.z, xcyczc.z, x, xc, xd), y, yc, yd), z, zc, zd));
    }



    protected float interGradient(double[] coord) {
        if (coord[0] < 0 || coord[0] > volume.getDimX() || coord[1] < 0 || coord[1] > volume.getDimY()
                || coord[2] < 0 || coord[2] > volume.getDimZ()) {
            return 0;
        }

        int xMin = (int) Math.floor(coord[0]);
        int yMin = (int) Math.floor(coord[1]);
        int zMin = (int) Math.floor(coord[2]);
        int xMax = (int) Math.ceil(coord[0]);
        int yMax = (int) Math.ceil(coord[1]);
        int zMax = (int) Math.ceil(coord[2]);

        if (xMax > volume.getDimX() - 1 || yMax > volume.getDimY() - 1 || zMax > volume.getDimZ() - 1) {
            xMin = xMax > volume.getDimX() - 1 ? volume.getDimX() - 1 : xMin;
            yMin = yMax > volume.getDimY() - 1 ? volume.getDimY() - 1 : yMin;
            zMin = zMax > volume.getDimZ() - 1 ? volume.getDimZ() - 1 : zMin;
            return gradients.getGradient(xMin, yMin, zMin).mag;
        }

        int value = TriInterpolation.triLerp(
                coord[0]/volume.getDimX(), coord[1]/volume.getDimY(), coord[2]/volume.getDimZ(),
                gradients.getGradient(xMin, yMin, zMin).mag,
                gradients.getGradient(xMin, yMax, zMin).mag,
                gradients.getGradient(xMin, yMin, zMax).mag,
                gradients.getGradient(xMin, yMax, zMax).mag,
                gradients.getGradient(xMax, yMin, zMin).mag,
                gradients.getGradient(xMax, yMax, zMin).mag,
                gradients.getGradient(xMax, yMin, zMax).mag,
                gradients.getGradient(xMax, yMax, zMax).mag
        );

        return value;
    }



    // todo check if this method is too complicated
    TFColor cal2dColor2(TFColor old, TFColor selected, short fxi, short fv, double r, VoxelGradient gradient, double[] viewVec) {
        double dfxi = gradient.mag;
        TFColor result = new TFColor(0, 0, 0, 1);
        double currentAlpha;
        if (dfxi == 0 && fxi == fv) {
            currentAlpha = selected.a;
        } else if (dfxi > 0 && fv >= (fxi - r * dfxi) && fv <= (fxi + r * dfxi)) {
            currentAlpha = selected.a * (1 - (fv - fxi) / (r * dfxi));
        } else {
            currentAlpha = 0;
        }

        if (shading) {
            double dotProducts = (viewVec[0] * gradient.x + viewVec[1] * gradient.y + viewVec[2] * gradient.z);
            if (dotProducts > 0) {
                double LN = dotProducts / gradient.mag;
                double pow = Math.pow(LN, alp);
                double tr = ambient * light.r + selected.r * diff * LN + spec * pow;
                double tg = ambient * light.g + selected.g * diff * LN + spec * pow;
                double tb = ambient * light.b + selected.b * diff * LN + spec * pow;
                result.r = old.r * (1 - currentAlpha) + currentAlpha * tr;
                result.g = old.g * (1 - currentAlpha) + currentAlpha * tg;
                result.b = old.b * (1 - currentAlpha) + currentAlpha * tb;
            }
        } else {
            result.r = old.r * (1 - currentAlpha) + currentAlpha * selected.r;
            result.g = old.g * (1 - currentAlpha) + currentAlpha * selected.g;
            result.b = old.b * (1 - currentAlpha) + currentAlpha * selected.b;
        }

        result.a = old.a * (1 - currentAlpha)+currentAlpha;
        /*
         result.r = selected.r * (1 - val.a) + val.a * val.r;
         result.g = selected.g * (1 - val.a) + val.a * val.g;
         result.b = selected.b * (1 - val.a) + val.a * val.b;
         */
        return result;
    }
    //END




    void slicer(double[] viewMatrix) {

        // clear image
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();
        TFColor voxelColor = new TFColor();

        
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                        + volumeCenter[0];
                pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                        + volumeCenter[1];
                pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                        + volumeCenter[2];

                int val = getVoxel(pixelCoord);
                
                // Map the intensity to a grey value by linear scaling
                voxelColor.r = val/max;
                voxelColor.g = voxelColor.r;
                voxelColor.b = voxelColor.r;
                voxelColor.a = val > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
                // Alternatively, apply the transfer function to obtain a color
                // voxelColor = tFunc.getColor(val);
                
                
                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }


        }

    }


    // Ours, method for switching functions
    void compute(double[] viewMatrix) {

            switch (function) {
                case FUNCTION_SLICER:
                    slicer(viewMatrix);
                    break;
                case FUNCTION_MIP:
                    MIP(viewMatrix);
                    break;
                case FUNCTION_COMPOSITING:
                    compositing(viewMatrix);
                    break;
                case FUNCTION_2DFUNC:
                    twoDimFunction2(viewMatrix);
//                twoDTransferDisplay(viewMatrix);
                    break;
//                case FUNCTION_MAXGRADIENT:
//                    twoDimFunction(viewMatrix);
//                    break;
                default:
                    slicer(viewMatrix);
                    break;
            }
        }



    // end

    private void drawBoundingBox(GL2 gl) {
        gl.glPushAttrib(GL2.GL_CURRENT_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glColor4d(1.0, 1.0, 1.0, 1.0);
        gl.glLineWidth(1.5f);
        gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glDisable(GL.GL_LINE_SMOOTH);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glPopAttrib();

    }




    @Override
    public void visualize(GL2 gl) {


        if (volume == null) {
            return;
        }

        drawBoundingBox(gl);

        gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX, viewMatrix, 0);

        long startTime = System.currentTimeMillis();
        //slicer(viewMatrix);   default visualization

        step = this.interactiveMode ? INTERACTIVE_MODE_STEP : NON_INTERACTIVE_MODE_STEP;
        granularity = this.interactiveMode ? INTERACTIVE_MODE_GRANULARITY : NON_INTERACTIVE_MODE_GRANULARITY;

        compute(viewMatrix);

        
        long endTime = System.currentTimeMillis();
        double runningTime = (endTime - startTime);
        panel.setSpeedLabel(Double.toString(runningTime));

        Texture texture = AWTTextureIO.newTexture(gl.getGLProfile(), image, false);

        gl.glPushAttrib(GL2.GL_LIGHTING_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        // draw rendered image as a billboard texture
        texture.enable(gl);
        texture.bind(gl);
        double halfWidth = image.getWidth() / 2;
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glBegin(GL2.GL_QUADS);
        gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        gl.glTexCoord2d(0.0, 0.0);
        gl.glVertex3d(-halfWidth, -halfWidth, 0.0);
        gl.glTexCoord2d(0.0, 1.0);
        gl.glVertex3d(-halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 1.0);
        gl.glVertex3d(halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 0.0);
        gl.glVertex3d(halfWidth, -halfWidth, 0.0);
        gl.glEnd();
        texture.disable(gl);
        texture.destroy(gl);
        gl.glPopMatrix();

        gl.glPopAttrib();


        if (gl.glGetError() > 0) {
            System.out.println("some OpenGL error: " + gl.glGetError());
        }

    }

    @Override
    public void changed() {
        for (int i=0; i < listeners.size(); i++) {
            listeners.get(i).changed();
        }
    }
}
