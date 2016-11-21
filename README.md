# NPSPyCaffe
Autopsy plugin that runs image detection algorithms using Caffe and Py-faster-rcnn on windows.


To install NPSPyCaffe:

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

