#!/bin/sh

mydir=`dirname $0`

ndk-build NDK_MODULE_PATH=$mydir "$@" 
