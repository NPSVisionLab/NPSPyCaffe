# NPSPyCaffe
Autopsy plugin that runs image detection algorithms using Caffe and Py-faster-rcnn on windows as the default.  You can also add other detecton algorithms like Py-RFCN by adding zip files to the default installation.


## To install NPSPyCaffe:

You need 64 bit python.  It is best to install Miniconda 2.7 64-bit Windows installer from the Miniconda website.

You will also need the Visual C++ Redistributable package for Visual Studio 2013 64 bit.  You can download this from the microsoft web site.

You need to install some additional python packages:

    \Miniconda2\scripts\conda install --yes scipy scikit-image pip pyyaml
    \Miniconda2\scripts\pip install protobuf

If you have a GPU you can install CUDA and cuDNN
Download CUDA toolkit 7.5 from nVidia website.
Download cuDNN v3 or cuDNN v4 from the nVidia website.  Unpack and upzip to %CUDA_PATH%.

The py-faster-rcnn detector type will run with the GPU or the CPU.  The default mode is to run in CPU mode.  GPU mode will run about 20 times faster.  To change the mode edit the detector.properties file (see below).
The pyrfcn detector type only currently runs in GPU mode. If you don't have a CUDA compatiable GPU it will not run!

You can download a netbeans module that contains the default plugin from:

    ftp://vision.nps.edu/org-nps-autopsypycaffe.nbm (600k)

You can download additional detector types:
    ftp://vison.nps.edu/autopsypycaffe-pyrfcn.zip  (pyrfcn detector type addition)

Install the org-nps-autopsypycaffe.nbm plugin from the Autopsy Plugin screen.
See below for where to unzip the detector type zips.

## NPSPyCaffe Plugin file structure

The detector types supported by NPSPyCaffe are subdirectories in the "NPSPyCaffe/detectors" directory.  By default, the py-faster-rcnn detector type is provided.  When you want to add a new detector type, you will unzip them into this directory.  
In each detector type directory will be a file called "detector.properties". This contains the global properties for that detector type.  For example to change the python executable that will be ran, change the py-faster-rcnn.python property in the detector.properties file in the py-faster-rcnn directory.
A detector is made up of a detector type and various parameters like the model file it was trained to use and others.  The parameters are in a file called <detector name>.properties.  For each one of these files in the detector type directory, a choice will be added to the injest configure screen for the NPSPyCaffe plugin.  The detector name will be the name of the file with the .properties stripped off. Adding a new detector is just a matter of providing the training files and creating a <detector name>.properties file and restarting Autopsy.  On restart is will look for files that end in .properties in the detector type directories and automatically add any it finds.
Once you install the plugin, the detector.properties file gets installed to the <Users directory>/AppData/Roaming/autopsy/NPSPyCaffe/detectors/<detector_type> directory (in the default case <detector_type> is py-faster-rcnn. We will show the default configuration file and describe each entry.

##Using NPSPyCaffe:


Default detector.properties file:

This file contains the NPSPyCaffe configuration file for a detector type.
A detector is based on a detector type which has the name of the directory
where it is located and a detector name which ties a detector type to a
configuration (model, classes, config, etc).  Each detector type directory
contains at least 2 or more property files. This file is named
detector.properties and contains information that applies to the detector
type.  There can be one or more of files named xxxx.properties where
xxxx is the name of the detector.  The properties in that file are  named:
<detector type>.<detector name>.<property>

The python to run if different from the default
    py-faster-rcnn.python = c:/Miniconda2/python.exe

The python script to execute a detection
    py-faster-rcnn.pyscript =  doPyFasterRCNN.py

output debug log info (true or false)
    py-faster-rcnn.debug = false

    py-faster-rcnn.gpu = true
    py-faster-rcnn.pythonPath = caffe-fast-rcnn/build/x64/Release/pycaffe;lib;caffe-fast-rcnn/python
    py-faster-rcnn.path = caffe-fast-rcnn/build/x64/Release;caffe-fast-rcnn/build/x64/Release/pycaffe/caffe
    py-faster-rcnn.pythonPathNoCuda = caffe-fast-rcnn/build/x64/Release/pycaffe_nocuda;lib;caffe-fast-rcnn/python
    py-faster-rcnn.pathNoCuda = caffe-fast-rcnn/build/x64/Release/pycaffe_nopcuda/caffe;caffe-fast-rcnn/build/x64/Release

Description of the detector.properties file:

    py-faster-rcnn.python = c:/Miniconda2/python.exe
This is where you need to specify where python is installed.  The plugin will try and find a valid python.  If it fails to find one then define it here.    


    py-faster-rcnn.pyscript = doPyFasterRCNN.py
This is the python script to run to actually do the detection.  This script will run the detection for each filename passed in stdin as well as the one passed in the startup arguments. 

    py-faster-rcnn.debug = false
If this is set to true more logging will be written out including all the information from the detection script.

    py-faster-rcnn.gpu = true
This indicates if you want to use any CUDA hardware accelleration if its available. This can speed up the processing of picture files by 20 times against a single CPU core.


Description of a demo.properties file (defines the demo detector)

	py-faster-rcnn.demo.classes = aeroplane, bicycle, bird, boat, bottle, bus, car,  cat, chair, cow, diningtable, dog, horse, motorbike, person, pottedplant, sheep, sofa, train, tvmonitor
These are the names of the object classes that the detector has been trained to detect.  They must be a comma seperated list in the same order as the detector was trained with not counting the backgrould class which is always 0.  So in this example "aeroplane" will be the class 1 in the training, bicycle class 2, and etc.

	py-faster-rcnn.demo.model = data/demo/vgg_cnn_m_1024_faster_rcnn_iter_300000.caffemodel
This is the model that was the product of the training of the detector.  This is the weights and bias values that drive the neural net.


    py-faster-rcnn.demo.prototxt = data/demo/test.prototxt
This is the file that defines the detection neural net.  Since we use the Caffe as the neural net library, the network is defined by Caffe's prototext network description syntax.

    py-faster-rcnn.demo.config = demo/faster_rcnn_alt_opt.yml
This is the configuration file for the py-faster-rcnn library.  It defines things like if the input images traing images are in black and white or color and if the network as a rectangle processing network attached (for image detection rectangles).

    py-faster-rcnn.demo.confidence = 0.8
This defines the minimum confidence value before the detection is considered valid. 

## Thanks to the following research projects:

    @inproceedings{renNIPS15fasterrcnn,
        Author = {Shaoqing Ren and Kaiming He and Ross Girshick and Jian Sun},
        Title = {Faster {R-CNN}: Towards Real-Time Object Detection
             with Region Proposal Networks},
        Booktitle = {Advances in Neural Information Processing Systems ({NIPS})},
        Year = {2015}
    }

    @article{dai16rfcn,
        Author = {Jifeng Dai, Yi Li, Kaiming He, Jian Sun},
        Title = {{R-FCN}: Object Detection via Region-based Fully Convolutional Networks},
        Journal = {arXiv preprint arXiv:1605.06409},
        Year = {2016}
    }

    @inproceedings{liu2016ssd,
        title = {{SSD}: Single Shot MultiBox Detector},
        author = {Liu, Wei and Anguelov, Dragomir and Erhan, Dumitru and Szegedy, Christian and Reed, Scott and Fu, Cheng-Yang and Berg, Alexander C.},
        booktitle = {ECCV},
        year = {2016}
    }



