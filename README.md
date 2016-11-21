# NPSPyCaffe
Autopsy plugin that runs image detection algorithms using Caffe and Py-faster-rcnn on windows.


## To install NPSPyCaffe:

You need 64 bit python.  It is best to install Miniconda 2.7 64-bit Windows installer from the Miniconda website.

You will also need the Visual C++ Redistributable package for Visual Studio 2013 64 bit.  You can download this from the microsoft web site.

You need to install some additional python packages:

    \Miniconda2\scripts\conda install --yes scipy scikit-image pip pyyaml
    \Miniconda2\scripts\pip install protobuf

If you have a GPU you can install CUDA and cuDNN
Download CUDA toolkit 7.5 from nVidia website.
Download cuDNN v3 or cuDNN v4 from the nVidia website.  Unpack and upzip to %CUDA_PATH%.

Install the org-nps-autopsypycaffe.nbm plugin from the Autopsy Plugin screen.

After you install the plugin, you can edit the NPSPyCaffe\detector.properties file in the plugin install directory (usually <user name>\AppData\Roaming\autopsy\NPSPyCaffe). Change the main.python property to reflect where you installed python. To get more debug information from the program set to true the main.debug property.


##Using NPSPyCaffe:

In the release\NPSPyCaffe directory resides the configuration file for the detector properties (detector.properties). Once you install the plugin, this file gets installed to the <Users directory>/AppData/Roaming/autopsy/NPSPyCaffe directory. In this file, you define some main properties that applies to all the detectors and then specific information about each detector.  The default installation provides a single detector.  We will show the default configuration file and describe the each entry.

Default detector.properties:

    # This file contains the NPSPyCaffe configuration file.  This allows users
    # to configure the detectors that the plugin will use.

    # The main section applies to all detectors
    #

    # The python to run
    main.python = c:/Miniconda2/python.exe
    # The python script to execute a detection
    main.pyscript = doPyFasterRCNN.py
    # output debug log info (true or false)
	main.debug = false
	# The detector choices to display to the user (these are comma seperated)
	main.detectors = demoDetect
	
	main.gpu = true
	
	# Each detector will have a section that starts with the name of the
	# detector defined in the main.detectors.
	
	######## Detector demoDetect ##########################
	# Property .classes
	#The object types that this detector will search for (these are comma seperate
	#These objects must be in the same order as they appear in the training.
	#For example aeroplane will be class 1, bicycle class 2 etc.
	#Also all classes must be on the same line!
	#Also do not include _background_ as it will get included as class 0
	#######################################################
	demoDetect.classes = aeroplane, bicycle, bird, boat, bottle, bus, car,  cat, chair, cow, diningtable, dog, horse, motorbike, person, pottedplant, sheep, sofa, train, tvmonitor
	#Text of what the detector does or is
	demoDetect.description = VGG_CNN_M1024 net configured for faster_rcnn_end2end
	# The py-faster-rcnn model to use
	demoDetect.model = demo/vgg_cnn_m_1024_faster_rcnn_iter_300000.caffemodel
    # The net to run while detecting
    demoDetect.prototxt = demo/test.prototxt
    # The detects config file
    demoDetect.config = demo/faster_rcnn_alt_opt.yml
    # The confidence threshold before reporting
    demoDetect.confidence = 0.8

    main.python = c:/Miniconda2/python.exe
This is where you need to specify where python is installed    


    main.pyscript = doPyFasterRCNN.py
This is the python script to run to actually do the detection.  This script will run the detection for each filename passed in stdin as well as the one passed in the startup arguments. 

    main.debug = false
If this is set to true more logging will be written out including all the information from the detection script.

    main.detectors = demoDetect
This is the names of the detectors defined in this property file.  The program will look for <detector_name>.<property> for each detector listed in the comma seperated list.  These detectors will appear as choices in the data ingest dialog when you select the NPSPyCaffe plugin.

    main.gpu = true
This indicates if you want to use any CUDA hardware accelleration if its available. This can speed up the processing of picture files by 20 times against a single CPU core.

	demoDetect.classes = aeroplane, bicycle, bird, boat, bottle, bus, car,  cat, chair, cow, diningtable, dog, horse, motorbike, person, pottedplant, sheep, sofa, train, tvmonitor
These are the names of the object classes that the detector has been trained to detect.  They must be a comma seperated list in the same order as the detector was trained with not counting the backgrould class which is always 0.  So in this example "aeroplane" will be the class 1 in the training, bicycle class 2, and etc.

	demoDetect.model = demo/vgg_cnn_m_1024_faster_rcnn_iter_300000.caffemodel
This is the model that was the product of the training of the detector.  This is the weights and bias values that drive the neural net.


    demoDetect.prototxt = demo/test.prototxt
This is the file that defines the detection neural net.  Since we use the Caffe as the neural net library, the network is defined by Caffe's prototext network description syntax.

    demoDetect.config = demo/faster_rcnn_alt_opt.yml
This is the configuration file for the py-faster-rcnn library.  It defines things like if the input images traing images are in black and white or color and if the network as a rectangle processing network attached (for image detection rectangles).

    demoDetect.confidence = 0.8
This defines the minimum confidence value before the detection is considered valid. 
