#!/bin/sh

STORM_PATH=${STORM_PATH:-/usr/local/storm}

if [ ! -d $STORM_PATH ]; then
  echo "No $STORM_PATH found, install storm first."
  exit 1
fi

if [ ! -f zeromq-2.1.7.tar.gz ]; then
  curl -O http://download.zeromq.org/zeromq-2.1.7.tar.gz
fi

rm -rf zeromq-2.1.7
tar xzvf zeromq-2.1.7.tar.gz
cd zeromq-2.1.7

./configure \
  --prefix=$STORM_PATH

make

make install

rm -rf jzmq
git clone --depth=1 https://github.com/nathanmarz/jzmq.git
cd jzmq

# See https://github.com/zeromq/jzmq/issues/114
sed -i -e 's/classdist_noinst.stamp/classnoinst.stamp/g' src/Makefile.am

./autogen.sh
./configure \
  --prefix=$STORM_PATH \
  --with-zeromq=$STORM_PATH

make

make install
