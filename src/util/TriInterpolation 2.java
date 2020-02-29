package util;

import volume.Volume;



public class TriInterpolation {



    public static short linearInter(double x, int q00, int q01) {
        return (short) ((1.0 - x) * q00 + x  * q01);
    }



    public static short linearInter(double x, float q00, float q01) {
        return (short) ((1.0 - x) * q00 + x  * q01);
    }

    public static short triLinearInter(double x, double y, double z, float q000, float q001, float q010, float q011,
                                       float q100, float q101, float q110, float q111) {
        short x00 = linearInter(x, q000, q100);
        short x10 = linearInter(x, q010, q110);
        short x01 = linearInter(x, q001, q101);
        short x11 = linearInter(x, q011, q111);

        short r0 = linearInter(y, x00, x01);
        short r1 = linearInter(y, x10, x11);

        return linearInter(z, r0, r1);
    }

    public static short triInterVoxel(double[] coord, Volume volume) {
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
            return volume.getVoxel(xMin, yMin, zMin);
        }

        short value = TriInterpolation.triLinearInter(
                coord[0]/volume.getDimX(), coord[1]/volume.getDimY(), coord[2]/volume.getDimZ(),
                volume.getVoxel(xMin, yMin, zMin),
                volume.getVoxel(xMin, yMax, zMin),
                volume.getVoxel(xMin, yMin, zMax),
                volume.getVoxel(xMin, yMax, zMax),
                volume.getVoxel(xMax, yMin, zMin),
                volume.getVoxel(xMax, yMax, zMin),
                volume.getVoxel(xMax, yMin, zMax),
                volume.getVoxel(xMax, yMax, zMax)
        );

        return value;
    }




}