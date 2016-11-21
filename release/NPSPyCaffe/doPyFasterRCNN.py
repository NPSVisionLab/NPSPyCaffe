#!/usr/bin/env python

# --------------------------------------------------------
# Faster R-CNN
# Copyright (c) 2015 Microsoft
# Licensed under The MIT License [see LICENSE for details]
# Written by Ross Girshick
# --------------------------------------------------------

"""
Demo script showing detections in sample images.

See README.md for installation instructions before running.
"""

#import _init_paths
from fast_rcnn.config import cfg, cfg_from_file
from fast_rcnn.test import im_detect
from fast_rcnn.nms_wrapper import nms

from utils.timer import Timer
import matplotlib.pyplot as plt
import matplotlib.image as mpimg
import numpy as np
import scipy.io as sio
import caffe, os, sys, cv2
import argparse
from contextlib import contextmanager
import shutil
from subprocess import Popen, PIPE
import shlex
import tempfile
import re
import time

from timeit import default_timer as timer

#from osgeo import gdal


CLASSES = ('__background__',
           'aeroplane', 'bicycle', 'bird', 'boat',
           'bottle', 'bus', 'car', 'cat', 'chair',
           'cow', 'diningtable', 'dog', 'horse',
           'motorbike', 'person', 'pottedplant',
           'sheep', 'sofa', 'train', 'tvmonitor')

classTuple = None

NETS = {'vgg16': ('VGG16',
                  'VGG16_faster_rcnn_final.caffemodel'),
        'vgg': ('VGG_CNN_M_1024',
                   'VGG_faster_rcnn_final.caffemodel'),
        'zf': ('ZF',
                  'ZF_faster_rcnn_final.caffemodel')}



class FileInput(object):
    def __init__(self, file):
        self.file = file

    def __enter__(self):
        return self

    def __exit__(self, *args, **kwargs):
        self.file.close()

    def __iter__(self):
        return self

    def next(self):
        line = self.file.readline()

        if line == None or line == "":
            print("line is none")
            sys.stdout.flush()
            raise StopIteration

        return line

def fileno(file_or_fd):
    fd = getattr(file_or_fd, 'fileno', lambda: file_or_fd)()
    if not isinstance(fd, int):
        raise ValueError("Expected a file (`.fileno()`) or a file descriptor")
    return fd


def vis_detections(im, class_name, dets, thresh=0.5):
    """Draw detected bounding boxes."""
    inds = np.where(dets[:, -1] >= thresh)[0]
    if len(inds) == 0:
        return

    im = im[:, :, (2, 1, 0)]
    fig, ax = plt.subplots(figsize=(12, 12))
    ax.imshow(im, aspect='equal')
    for i in inds:
        bbox = dets[i, :4]
        score = dets[i, -1]

        ax.add_patch(
            plt.Rectangle((bbox[0], bbox[1]),
                          bbox[2] - bbox[0],
                          bbox[3] - bbox[1], fill=False,
                          edgecolor='red', linewidth=3.5)
            )
        ax.text(bbox[0], bbox[1] - 2,
                '{:s} {:.3f}'.format(class_name, score),
                bbox=dict(facecolor='blue', alpha=0.5),
                fontsize=14, color='white')

    ax.set_title(('{} detections with '
                  'p({} | box) >= {:.1f}').format(class_name, class_name,
                                                  thresh),
                  fontsize=14)
    plt.axis('off')
    plt.tight_layout()
    plt.draw()

def demo(net, im_file, conf):
    """Detect object classes in an image using pre-computed object proposals."""
    res = []
    res.append("file: " + im_file)
    if cfg.TRAIN.IS_COLOR == True:
        im = cv2.imread(im_file)
    else:
        # Load the demo image as gray scale
        im = cv2.imread(im_file, flags=cv2.CV_LOAD_IMAGE_GRAYSCALE)

    if im is None:
        print("File could not be opened! " + im_file)
        return res
    if im.shape[0] < 64 or im.shape[1] < 64:
        print("Detection failed, file too small: " + im_file)
        return res
    # convert to rgb repeated in each channel

    # Detect all object classes and regress object bounds
    timer = Timer()
    timer.tic()
    scores, boxes = im_detect(net, im)
    timer.toc()
    print ('Detection took {:.3f}s for '
           '{:d} object proposals').format(timer.total_time, boxes.shape[0])

    # Visualize detections for each class

    NMS_THRESH = 0.3

    for cls_ind, cls in enumerate(classTuple[1:]):
        cls_ind += 1 # because we skipped background
        cls_boxes = boxes[:, 4*cls_ind:4*(cls_ind + 1)]
        cls_scores = scores[:, cls_ind]
        dets = np.hstack((cls_boxes,
                          cls_scores[:, np.newaxis])).astype(np.float32)
        keep = nms(dets, NMS_THRESH)
        dets = dets[keep, :]
        inds = np.where(dets[:,-1] >= conf)[0]
        for i in inds:
            bbox = dets[i, :4]
            score = dets[i, -1]
            res.append(cls + " {0} {1} {2} {3} {4}".format(score, bbox[0], bbox[1], bbox[2] - bbox[0], bbox[3] - bbox[1]))

    return res

def parse_args():
    """Parse input arguments."""
    parser = argparse.ArgumentParser(description='Faster R-CNN demo')
    parser.add_argument('--gpu', dest='gpu_id', help='GPU device id to use [0]',
                        default=0, type=int)
    parser.add_argument('--cpu', dest='cpu_mode',
                        help='Use CPU mode (overrides --gpu)',
                        action='store_true')
    parser.add_argument('--net', dest='demo_net', help='Network to use [zf]',
                        choices=NETS.keys(), default='vgg')
    parser.add_argument('--cfg', dest='cfg_file',
                        help='optional config file',
                        default=None, type=str)
    parser.add_argument('--classes', dest='classes',
                        help='comma seperated classes in class order',
                        default=None, type=str)
    parser.add_argument('--confidence', dest='confidence',
                         help='Confidence threshold for detection',
                         default=0.8, type=float)
    parser.add_argument('--model', dest='model_file',
                        help='caffe model file',
                        default=None, type=str)
    parser.add_argument('--proto', dest='proto_file',
                        help='caffe prototext file',
                        default=None, type=str)
    # parser.add_argument('--split', dest='split_size',
    #                     help='width && height for split up images',
    #                     action='store', type=int)
    # parser.add_argument('--tiles', dest='tile_path',
    #                     help='image tile output path',
    #                     default=None, type=str)
    parser.add_argument('file', help="Image file or dir to process",
                        type=str)

    args = parser.parse_args()

    return args


def doWriteToHDFS(dirname, fname) :
    basename = os.path.basename(fname)
    hname = os.path.join(dirname, basename)
    put = Popen(["hdfs", "dfs", "-put", fname, hname],
            stdout=PIPE, stderr = PIPE)
    stdout, stderr = put.communicate()
    print stderr
    return hname

import signal, errno
from contextlib import contextmanager

@contextmanager
def timeout(seconds):
    def timeout_handler(signum, frame):
        pass

    orig_handler = signal.signal(signal.SIGALRM, timeout_handler)
    try:
        signal.alarm(seconds)
        yield
    finally:
        signal.alarm(0)
        signal.signal(signal.SIGALRM, orig_handler)



def outputDetects(detects):
    # Write out the result to stdout
    for d in detects:
        save_stdout.write(d)
        save_stdout.write("\n")
    save_stdout.flush()


if __name__ == '__main__':
    #debug profiling
    #import cProfile

    save_stdout = sys.stdout
    sys.stdout = sys.stderr
    #debug force stdout to flush optut
    sys.stdout = os.fdopen(sys.stdout.fileno(), "w", 0) 


    #debug profiling
    #profile = cProfile.Profile()
    #profile.enable()

    cfg.TEST.HAS_RPN = True  # Use RPN for proposals

    args = parse_args()
    if args.cfg_file is not None:
        print("using config " + args.cfg_file)
        cfg_from_file(args.cfg_file)
    if cfg.TRAIN.IS_COLOR == True:
        print("We are configured for color")
    else:
        print("We are configured for b/w")

    ifile = args.file

    print("Using confidence of {0}".format(args.confidence))

    if args.model_file is not None:
        caffemodel = args.model_file
    if args.proto_file is not None:
        prototxt = args.proto_file

    if not os.path.isfile(caffemodel):
        raise IOError(('{:s} not found.\nDid you train it?'
                       ).format(caffemodel))

    if args.cpu_mode:
        caffe.set_mode_cpu()
    else:
        caffe.set_mode_gpu()
        caffe.set_device(args.gpu_id)
        cfg.GPU_ID = args.gpu_id

    if args.classes:
        classList = args.classes.split(',')
        # We always need background to be first!
        classList.insert(0, '_background_')
        classTuple = tuple(classList)
        print("Looking for {0} number of classes.".format(len(classTuple)));

    #debug
    doDetect = True

    fileList = [ifile]

    #fileList = []
    #for f in os.listdir(ifile):
    #    next = os.path.join(ifile,f)
    #    if os.path.isfile(next):
    #        fileList.append(next)

    if doDetect == True:
        # debug
        net = caffe.Net(prototxt, caffemodel, caffe.TEST)
        print '\n\nLoaded network {:s}'.format(caffemodel)
        # Warmup on a dummy image
        #im = 128 * np.ones((300, 500, 1), dtype=np.uint8)
        im = 128 * np.ones((300, 500, 3), dtype=np.uint8)
        for i in xrange(2):
            _, _= im_detect(net, im)

        for nextf in fileList:
            print('detection for ' + nextf)
            res = demo(net, nextf, args.confidence)
            outputDetects(res)
        print("part1 complete")
        sys.stdout.flush()

    try:
        print("reading stdin")
        sys.stdout.flush()
        with FileInput(sys.stdin) as f:
            for line in f:
                print("line: " + line)
                next = line.strip()
                print('detection for next file ' + next)
                if next == "close":
                    break
                res = demo(net, next, args.confidence)
                outputDetects(res)
    except Exception:
       print("Could not read from stdin")

    #debug profiling
    #profile.disable()
    #profile.print_stats(sort='time')

    sys.exit(0)

