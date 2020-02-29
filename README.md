# Volume Rendering and Visualisation
### co-author: [Marina Angelovska](https://github.com/marinaangelovska)

## Description
Direct volume rendering via 3D textured has proved to be an efficient tool for display and visual analysis of volumetric scalar fields.  Raycasting and 2D Transfer Functions have been implemented on a skeleton code with a GUI interface. Regarding the raycasting technique, Trilinear Interpolation, Maximum Intensity Projection and Compositing methods have been developed. On the other hand, for the 2D Transfer Functions it has been used the Gradient-based Opacity Weighting, Illumination model (Shading).  

## Requirements
* java 8
* adding the libraries from _lib_ to the project on the chosen IDE

## Results
The following pictures show respectively, some objects after using MIP, the same objects after using Compositing and the difference between shading and no shading on a 2D Transfer Function:

![MIP](https://github.com/dadadima94/volume-rendering/blob/master/images/01.png)

![Compositing](https://github.com/dadadima94/volume-rendering/blob/master/images/01-2.png)

![Shading vs no shading](https://github.com/dadadima94/volume-rendering/blob/master/images/02.png)

You can find more pictures in the _images_ directory.
