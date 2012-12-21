# PyAudio : Python Bindings for PortAudio.

# Copyright (c) 2006-2010 Hubert Pham

# Permission is hereby granted, free of charge, to any person obtaining
# a copy of this software and associated documentation files (the
# "Software"), to deal in the Software without restriction, including
# without limitation the rights to use, copy, modify, merge, publish,
# distribute, sublicense, and/or sell copies of the Software, and to
# permit persons to whom the Software is furnished to do so, subject to
# the following conditions:

# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.

# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
# NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
# LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
# OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
# WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.


""" PyAudio : Python Bindings for PortAudio v19.

**These bindings only support PortAudio blocking mode.**

:var PaSampleFormat:
  A list of all PortAudio ``PaSampleFormat`` value constants.

  See: `paInt32`, `paInt24`, `paInt16`, `paInt8`, and `paUInt8`.

:var PaHostApiTypeId:
  A list of all PortAudio ``PaHostApiTypeId`` constants.

  See: `paInDevelopment`, `paDirectSound`, `paMME`, `paASIO`,
  `paSoundManager`, `paCoreAudio`, `paOSS`, `paALSA`, `paAL`, *et al...*

:var PaErrorCode:
  A list of all PortAudio ``PaErrorCode`` constants.
  Typically, error code constants are included in Python
  exception objects (as the second argument).

  See: `paNoError`, `paNotInitialized`, `paUnanticipatedHostError`,
  *et al...*

:group PortAudio Constants:
  PaSampleFormat, PaHostApiTypeId, PaErrorCode

:group PaSampleFormat Values:
  paFloat32, paInt32, paInt24, paInt16,
  paInt8, paUInt8, paCustomFormat

:group PaHostApiTypeId Values:
  paInDevelopment, paDirectSound, paMME, paASIO,
  paSoundManager, paCoreAudio, paOSS, paALSA
  paAL, paBeOS, paWDMKS, paJACK, paWASAPI, paNoDevice

:group PaErrorCode Values:
  paNoError,
  paNotInitialized, paUnanticipatedHostError,
  paInvalidChannelCount, paInvalidSampleRate,
  paInvalidDevice, paInvalidFlag,
  paSampleFormatNotSupported, paBadIODeviceCombination,
  paInsufficientMemory, paBufferTooBig,
  paBufferTooSmall, paNullCallback,
  paBadStreamPtr, paTimedOut,
  paInternalError, paDeviceUnavailable,
  paIncompatibleHostApiSpecificStreamInfo, paStreamIsStopped,
  paStreamIsNotStopped, paInputOverflowed,
  paOutputUnderflowed, paHostApiNotFound,
  paInvalidHostApi, paCanNotReadFromACallbackStream,
  paCanNotWriteToACallbackStream,
  paCanNotReadFromAnOutputOnlyStream,
  paCanNotWriteToAnInputOnlyStream,
  paIncompatibleStreamHostApi

:group Stream Conversion Convenience Functions:
  get_sample_size, get_format_from_width

:group PortAudio version:
  get_portaudio_version, get_portaudio_version_text

:sort: PaSampleFormat, PaHostApiTypeId, PaErrorCode
:sort: PortAudio Constants, PaSampleFormat Values,
       PaHostApiTypeId Values, PaErrorCode Values

"""

__author__ = "Hubert Pham"
__version__ = "0.2.4"
__docformat__ = "restructuredtext en"

import sys

# attempt to import PortAudio
try:
    import _portaudio as pa
except ImportError:
    print "Please build and install the PortAudio Python " +\
          "bindings first."
    sys.exit(-1)


# Try to use Python 2.4's built in `set'
try:
    a = set()
    del a
except NameError:
    from sets import Set as set

############################################################
# GLOBALS
############################################################

##### PaSampleFormat Sample Formats #####

paFloat32 = pa.paFloat32
paInt32 = pa.paInt32
paInt24 = pa.paInt24
paInt16 = pa.paInt16
paInt8 = pa.paInt8
paUInt8 = pa.paUInt8
paCustomFormat = pa.paCustomFormat

# group them together for epydoc
PaSampleFormat = ['paFloat32', 'paInt32', 'paInt24', 'paInt16',
                  'paInt8', 'paUInt8', 'paCustomFormat']


###### HostAPI TypeId #####

paInDevelopment = pa.paInDevelopment
paDirectSound = pa.paDirectSound
paMME = pa.paMME
paASIO = pa.paASIO
paSoundManager = pa.paSoundManager
paCoreAudio = pa.paCoreAudio
paOSS = pa.paOSS
paALSA = pa.paALSA
paAL = pa.paAL
paBeOS = pa.paBeOS
paWDMKS = pa.paWDMKS
paJACK = pa.paJACK
paWASAPI = pa.paWASAPI
paNoDevice = pa.paNoDevice

# group them together for epydoc
PaHostApiTypeId = ['paInDevelopment', 'paDirectSound', 'paMME',
                   'paASIO', 'paSoundManager', 'paCoreAudio',
                   'paOSS', 'paALSA', 'paAL', 'paBeOS',
                   'paWDMKS', 'paJACK', 'paWASAPI', 'paNoDevice']

###### portaudio error codes #####

paNoError = pa.paNoError
paNotInitialized = pa.paNotInitialized
paUnanticipatedHostError = pa.paUnanticipatedHostError
paInvalidChannelCount = pa.paInvalidChannelCount
paInvalidSampleRate = pa.paInvalidSampleRate
paInvalidDevice = pa.paInvalidDevice
paInvalidFlag = pa.paInvalidFlag
paSampleFormatNotSupported = pa.paSampleFormatNotSupported
paBadIODeviceCombination = pa.paBadIODeviceCombination
paInsufficientMemory = pa.paInsufficientMemory
paBufferTooBig = pa.paBufferTooBig
paBufferTooSmall = pa.paBufferTooSmall
paNullCallback = pa.paNullCallback
paBadStreamPtr = pa.paBadStreamPtr
paTimedOut = pa.paTimedOut
paInternalError = pa.paInternalError
paDeviceUnavailable = pa.paDeviceUnavailable
paIncompatibleHostApiSpecificStreamInfo = pa.paIncompatibleHostApiSpecificStreamInfo
paStreamIsStopped = pa.paStreamIsStopped
paStreamIsNotStopped = pa.paStreamIsNotStopped
paInputOverflowed = pa.paInputOverflowed
paOutputUnderflowed = pa.paOutputUnderflowed
paHostApiNotFound = pa.paHostApiNotFound
paInvalidHostApi = pa.paInvalidHostApi
paCanNotReadFromACallbackStream = pa.paCanNotReadFromACallbackStream
paCanNotWriteToACallbackStream = pa.paCanNotWriteToACallbackStream
paCanNotReadFromAnOutputOnlyStream = pa.paCanNotReadFromAnOutputOnlyStream
paCanNotWriteToAnInputOnlyStream = pa.paCanNotWriteToAnInputOnlyStream
paIncompatibleStreamHostApi = pa.paIncompatibleStreamHostApi

# group them together for epydoc
PaErrorCode = ['paNoError',
               'paNotInitialized', 'paUnanticipatedHostError',
               'paInvalidChannelCount', 'paInvalidSampleRate',
               'paInvalidDevice', 'paInvalidFlag',
               'paSampleFormatNotSupported', 'paBadIODeviceCombination',
               'paInsufficientMemory', 'paBufferTooBig',
               'paBufferTooSmall', 'paNullCallback',
               'paBadStreamPtr', 'paTimedOut',
               'paInternalError', 'paDeviceUnavailable',
               'paIncompatibleHostApiSpecificStreamInfo', 'paStreamIsStopped',
               'paStreamIsNotStopped', 'paInputOverflowed',
               'paOutputUnderflowed', 'paHostApiNotFound',
               'paInvalidHostApi', 'paCanNotReadFromACallbackStream',
               'paCanNotWriteToACallbackStream',
               'paCanNotReadFromAnOutputOnlyStream',
               'paCanNotWriteToAnInputOnlyStream',
               'paIncompatibleStreamHostApi']

############################################################
# Convenience Functions
############################################################

def get_sample_size(format):
    """
    Returns the size (in bytes) for the specified
    sample `format` (a `PaSampleFormat` constant).

    :param `format`:
       PortAudio sample format constant `PaSampleFormat`.

    :raises ValueError: Invalid specified `format`.

    :rtype: int
    """

    return pa.get_sample_size(format)

def get_format_from_width(width, unsigned = True):
    """
    Returns a PortAudio format constant for
    the specified `width`.

    :param `width`:
      The desired sample width in bytes (1, 2, 3, or 4)
    :param `unsigned`:
      For 1 byte width, specifies signed or unsigned
      format.

    :raises ValueError: for invalid `width`
    :rtype: `PaSampleFormat`

    """

    if width == 1:
        if unsigned:
            return paUInt8
        else:
            return paInt8
    elif width == 2:
        return paInt16
    elif width == 3:
        return paInt24
    elif width == 4:
        return paFloat32
    else:
        raise ValueError, "Invalid width: %d" % width


############################################################
# Versioning
############################################################

def get_portaudio_version():
    """
    Returns portaudio version.

    :rtype: str """

    return pa.get_version()

def get_portaudio_version_text():
    """
    Returns PortAudio version as a text string.

    :rtype: str """

    return pa.get_version_text()

############################################################
# Wrapper around _portaudio Stream (Internal)
############################################################

# Note: See PyAudio class below for main export.

class Stream:

    """
    PortAudio Stream Wrapper. Use `PyAudio.open` to make a new
    `Stream`.

    :group Opening and Closing:
      __init__, close

    :group Stream Info:
      get_input_latency, get_output_latency, get_time, get_cpu_load

    :group Stream Management:
      start_stream, stop_stream, is_active, is_stopped

    :group Input Output:
      write, read, get_read_available, get_write_available

    """

    def __init__(self,
                 PA_manager,
                 rate,
                 channels,
                 format,
                 input = False,
                 output = False,
                 input_device_index = None,
                 output_device_index = None,
                 frames_per_buffer = 1024,
                 start = True,
                 input_host_api_specific_stream_info = None,
                 output_host_api_specific_stream_info = None):
        """
        Initialize a stream; this should be called by
        `PyAudio.open`. A stream can either be input, output, or both.


        :param `PA_manager`: A reference to the managing `PyAudio` instance
        :param `rate`: Sampling rate
        :param `channels`: Number of channels
        :param `format`: Sampling size and format. See `PaSampleFormat`.
        :param `input`: Specifies whether this is an input stream.
            Defaults to False.
        :param `output`: Specifies whether this is an output stream.
            Defaults to False.
        :param `input_device_index`: Index of Input Device to use.
            Unspecified (or None) uses default device.
            Ignored if `input` is False.
        :param `output_device_index`:
            Index of Output Device to use.
            Unspecified (or None) uses the default device.
            Ignored if `output` is False.
        :param `frames_per_buffer`: Specifies the number of frames per buffer.
        :param `start`: Start the stream running immediately.
            Defaults to True. In general, there is no reason to set
            this to false.
        :param `input_host_api_specific_stream_info`: Specifies a host API
            specific stream information data structure for input.
            See `PaMacCoreStreamInfo`.
        :param `output_host_api_specific_stream_info`: Specifies a host API
            specific stream information data structure for output.
            See `PaMacCoreStreamInfo`.

        :raise ValueError: Neither input nor output
         are set True.

        """

        # no stupidity allowed
        if not (input or output):
            raise ValueError, \
                  "Must specify an input or output " +\
                  "stream."

        # remember parent
        self._parent = PA_manager

        # remember if we are an: input, output (or both)
        self._is_input = input
        self._is_output = output

        # are we running?
        self._is_running = start

        # remember some parameters
        self._rate = rate
        self._channels = channels
        self._format = format
        self._frames_per_buffer = frames_per_buffer

        arguments = {
            'rate' : rate,
            'channels' : channels,
            'format' : format,
            'input' : input,
            'output' : output,
            'input_device_index' : input_device_index,
            'output_device_index' : output_device_index,
            'frames_per_buffer' : frames_per_buffer}

        if input_host_api_specific_stream_info:
            _l = input_host_api_specific_stream_info
            arguments[
                'input_host_api_specific_stream_info'
                ] = _l._get_host_api_stream_object()

        if output_host_api_specific_stream_info:
            _l = output_host_api_specific_stream_info
            arguments[
                'output_host_api_specific_stream_info'
                ] = _l._get_host_api_stream_object()

        # calling pa.open returns a stream object
        self._stream = pa.open(**arguments)

        self._input_latency = self._stream.inputLatency
        self._output_latency = self._stream.outputLatency

        if self._is_running:
            pa.start_stream(self._stream)


    def close(self):
        """ Close the stream """

        pa.close(self._stream)

        self._is_running = False

        self._parent._remove_stream(self)


    ############################################################
    # Stream Info
    ############################################################

    def get_input_latency(self):
        """
        Return the input latency.

        :rtype: float
        """

        return self._stream.inputLatency


    def get_output_latency(self):
        """
        Return the input latency.

        :rtype: float
        """

        return self._stream.outputLatency

    def get_time(self):
        """
        Return stream time.

        :rtype: float

        """

        return pa.get_stream_time(self._stream)

    def get_cpu_load(self):
        """
        Return the CPU load.

        (Note: this is always 0.0 for the blocking API.)

        :rtype: float

        """

        return pa.get_stream_cpu_load(self._stream)


    ############################################################
    # Stream Management
    ############################################################

    def start_stream(self):
        """ Start the stream. """

        if self._is_running:
            return

        pa.start_stream(self._stream)
        self._is_running = True

    def stop_stream(self):

        """ Stop the stream. Once the stream is stopped,
        one may not call write or read. However, one may
        call start_stream to resume the stream. """

        if not self._is_running:
            return

        pa.stop_stream(self._stream)
        self._is_running = False

    def is_active(self):
        """ Returns whether the stream is active.

        :rtype: bool """

        return pa.is_stream_active(self._stream)

    def is_stopped(self):
        """ Returns whether the stream is stopped.

        :rtype: bool """

        return pa.is_stream_stopped(self._stream)


    ############################################################
    # Reading/Writing
    ############################################################

    def write(self, frames, num_frames = None,
              exception_on_underflow = False):

        """
        Write samples to the stream.


        :param `frames`:
           The frames of data.
        :param `num_frames`:
           The number of frames to write.
           Defaults to None, in which this value will be
           automatically computed.
        :param `exception_on_underflow`:
           Specifies whether an exception should be thrown
           (or silently ignored) on buffer underflow. Defaults
           to False for improved performance, especially on
           slower platforms.

        :raises IOError: if the stream is not an output stream
         or if the write operation was unsuccessful.

        :rtype: `None`

        """

        if not self._is_output:
            raise IOError("Not output stream",
                          paCanNotWriteToAnInputOnlyStream)

        if num_frames == None:
            # determine how many frames to read
            width = get_sample_size(self._format)
            num_frames = len(frames) / (self._channels * width)
            #print len(frames), self._channels, self._width, num_frames

        pa.write_stream(self._stream, frames, num_frames,
                        exception_on_underflow)


    def read(self, num_frames):
        """
        Read samples from the stream.


        :param `num_frames`:
           The number of frames to read.

        :raises IOError: if stream is not an input stream
         or if the read operation was unsuccessful.

        :rtype: str

        """

        if not self._is_input:
            raise IOError("Not input stream",
                          paCanNotReadFromAnOutputOnlyStream)

        return pa.read_stream(self._stream, num_frames)

    def get_read_available(self):
        """
        Return the number of frames that can be read
        without waiting.

        :rtype: int
        """

        return pa.get_stream_read_available(self._stream)


    def get_write_available(self):
        """
        Return the number of frames that can be written
        without waiting.

        :rtype: int

        """

        return pa.get_stream_write_available(self._stream)



############################################################
# Main Export
############################################################

class PyAudio:

    """
    Python interface to PortAudio. Provides methods to:
     - initialize and terminate PortAudio
     - open and close streams
     - query and inspect the available PortAudio Host APIs
     - query and inspect the available PortAudio audio
       devices

    Use this class to open and close streams.

    :group Stream Management:
      open, close

    :group Host API:
      get_host_api_count, get_default_host_api_info,
      get_host_api_info_by_type, get_host_api_info_by_index,
      get_device_info_by_host_api_device_index

    :group Device API:
      get_device_count, is_format_supported,
      get_default_input_device_info,
      get_default_output_device_info,
      get_device_info_by_index

    :group Stream Format Conversion:
      get_sample_size, get_format_from_width

    """

    ############################################################
    # Initialization and Termination
    ############################################################

    def __init__(self):

        """ Initialize PortAudio. """

        pa.initialize()
        self._streams = set()

    def terminate(self):

        """ Terminate PortAudio.

        :attention: Be sure to call this method for every
          instance of this object to release PortAudio resources.
        """

        for stream in self._streams:
            stream.close()

        self._streams = set()

        pa.terminate()


    ############################################################
    # Stream Format
    ############################################################

    def get_sample_size(self, format):
        """
        Returns the size (in bytes) for the specified
        sample `format` (a `PaSampleFormat` constant).


        :param `format`:
           Sample format constant (`PaSampleFormat`).

        :raises ValueError: Invalid specified `format`.

        :rtype: int
        """

        return pa.get_sample_size(format)


    def get_format_from_width(self, width, unsigned = True):
        """
        Returns a PortAudio format constant for
        the specified `width`.

        :param `width`:
            The desired sample width in bytes (1, 2, 3, or 4)
        :param `unsigned`:
            For 1 byte width, specifies signed or unsigned format.

        :raises ValueError: for invalid `width`

        :rtype: `PaSampleFormat`
        """

        if width == 1:
            if unsigned:
                return paUInt8
            else:
                return paInt8
        elif width == 2:
            return paInt16
        elif width == 3:
            return paInt24
        elif width == 4:
            return paFloat32
        else:
            raise ValueError, "Invalid width: %d" % width


    ############################################################
    # Stream Factory
    ############################################################

    def open(self, *args, **kwargs):
        """
        Open a new stream. See constructor for
        `Stream.__init__` for parameter details.

        :returns: `Stream` """

        stream = Stream(self, *args, **kwargs)
        self._streams.add(stream)
        return stream


    def close(self, stream):
        """
        Close a stream. Typically use `Stream.close` instead.

        :param `stream`:
           An instance of the `Stream` object.

        :raises ValueError: if stream does not exist.
        """

        if stream not in self._streams:
            raise ValueError, "Stream `%s' not found" % str(stream)

        stream.close()


    def _remove_stream(self, stream):
        """
        Internal method. Removes a stream.

        :param `stream`:
           An instance of the `Stream` object.

        """

        if stream in self._streams:
            self._streams.remove(stream)


    ############################################################
    # Host API Inspection
    ############################################################

    def get_host_api_count(self):
        """
        Return the number of PortAudio Host APIs.

        :rtype: int
        """

        return pa.get_host_api_count()

    def get_default_host_api_info(self):
        """
        Return a dictionary containing the default Host API
        parameters. The keys of the dictionary mirror the data fields
        of PortAudio's ``PaHostApiInfo`` structure.

        :raises IOError: if no default input device available
        :rtype: dict

        """

        defaultHostApiIndex = pa.get_default_host_api()
        return self.get_host_api_info_by_index(defaultHostApiIndex)


    def get_host_api_info_by_type(self, host_api_type):
        """
        Return a dictionary containing the Host API parameters for the
        host API specified by the `host_api_type`. The keys of the
        dictionary mirror the data fields of PortAudio's ``PaHostApiInfo``
        structure.


        :param `host_api_type`:
           The desired Host API (`PaHostApiTypeId` constant).

        :raises IOError: for invalid `host_api_type`
        :rtype: dict
        """

        index = pa.host_api_type_id_to_host_api_index(host_api_type)
        return self.get_host_api_info_by_index(index)


    def get_host_api_info_by_index(self, host_api_index):
        """
        Return a dictionary containing the Host API parameters for the
        host API specified by the `host_api_index`. The keys of the
        dictionary mirror the data fields of PortAudio's ``PaHostApiInfo``
        structure.

        :param `host_api_index`: The host api index.

        :raises IOError: for invalid `host_api_index`

        :rtype: dict
        """

        return self._make_host_api_dictionary(
            host_api_index,
            pa.get_host_api_info(host_api_index)
            )

    def get_device_info_by_host_api_device_index(self,
                                                 host_api_index,
                                                 host_api_device_index):
        """
        Return a dictionary containing the Device parameters for a
        given Host API's n'th device. The keys of the dictionary
        mirror the data fields of PortAudio's ``PaDeviceInfo`` structure.


        :param `host_api_index`:
           The Host API index number.
        :param `host_api_device_index`:
           The *n* 'th device of the host API.

        :raises IOError: for invalid indices

        :rtype: dict
        """

        long_method_name = pa.host_api_device_index_to_device_index
        device_index = long_method_name(host_api_index,
                                        host_api_device_index)
        return self.get_device_info_by_index(device_index)


    def _make_host_api_dictionary(self, index, host_api_struct):
        """
        Internal method to create Host API dictionary
        that mirrors PortAudio's ``PaHostApiInfo`` structure.

        :rtype: dict
        """

        return {'index' : index,
                'structVersion' : host_api_struct.structVersion,
                'type' : host_api_struct.type,
                'name' : host_api_struct.name,
                'deviceCount' : host_api_struct.deviceCount,
                'defaultInputDevice' : host_api_struct.defaultInputDevice,
                'defaultOutputDevice' : host_api_struct.defaultOutputDevice}

    ############################################################
    # Device Inspection
    ############################################################

    def get_device_count(self):
        """
        Return the number of PortAudio Host APIs.

        :rtype: int
        """

        return pa.get_device_count()

    def is_format_supported(self, rate,
                            input_device = None,
                            input_channels = None,
                            input_format = None,
                            output_device = None,
                            output_channels = None,
                            output_format = None):
        """
        Check to see if specified device configuration
        is supported. Returns True if the configuration
        is supported; throws a ValueError exception otherwise.

        :param `rate`:
           Specifies the desired rate (in Hz)
        :param `input_device`:
           The input device index. Specify `None` (default) for
           half-duplex output-only streams.
        :param `input_channels`:
           The desired number of input channels. Ignored if
           `input_device` is not specified (or `None`).
        :param `input_format`:
           PortAudio sample format constant defined
           in this module
        :param `output_device`:
           The output device index. Specify `None` (default) for
           half-duplex input-only streams.
        :param `output_channels`:
           The desired number of output channels. Ignored if
           `input_device` is not specified (or `None`).
        :param `output_format`:
           PortAudio sample format constant (`PaSampleFormat`).

        :rtype: bool
        :raises ValueError: tuple containing:
           (error string, PortAudio error code `PaErrorCode`).

        """

        if input_device == None and output_device == None:
            raise ValueError("must specify stream format for input, " +\
                             "output, or both", paInvalidDevice);

        kwargs = {}

        if input_device != None:
            kwargs['input_device'] = input_device
            kwargs['input_channels'] = input_channels
            kwargs['input_format'] = input_format

        if output_device != None:
            kwargs['output_device'] = output_device
            kwargs['output_channels'] = output_channels
            kwargs['output_format'] = output_format

        return pa.is_format_supported(rate, **kwargs)


    def get_default_input_device_info(self):
        """
        Return the default input Device parameters as a
        dictionary. The keys of the dictionary mirror the data fields
        of PortAudio's ``PaDeviceInfo`` structure.

        :raises IOError: No default input device available.
        :rtype: dict
        """

        device_index = pa.get_default_input_device()
        return self.get_device_info_by_index(device_index)

    def get_default_output_device_info(self):
        """
        Return the default output Device parameters as a
        dictionary. The keys of the dictionary mirror the data fields
        of PortAudio's ``PaDeviceInfo`` structure.

        :raises IOError: No default output device available.
        :rtype: dict
        """

        device_index = pa.get_default_output_device()
        return self.get_device_info_by_index(device_index)


    def get_device_info_by_index(self, device_index):
        """
        Return the Device parameters for device specified in
        `device_index` as a dictionary. The keys of the dictionary
        mirror the data fields of PortAudio's ``PaDeviceInfo``
        structure.

        :param `device_index`: The device index.
        :raises IOError: Invalid `device_index`.
        :rtype: dict
        """

        return self._make_device_info_dictionary(
            device_index,
            pa.get_device_info(device_index)
            )

    def _make_device_info_dictionary(self, index, device_info):
        """
        Internal method to create Device Info dictionary
        that mirrors PortAudio's ``PaDeviceInfo`` structure.

        :rtype: dict
        """

        return {'index' : index,
                'structVersion' : device_info.structVersion,
                'name' : device_info.name,
                'hostApi' : device_info.hostApi,
                'maxInputChannels' : device_info.maxInputChannels,
                'maxOutputChannels' : device_info.maxOutputChannels,
                'defaultLowInputLatency' :
                device_info.defaultLowInputLatency,
                'defaultLowOutputLatency' :
                device_info.defaultLowOutputLatency,
                'defaultHighInputLatency' :
                device_info.defaultHighInputLatency,
                'defaultHighOutputLatency' :
                device_info.defaultHighOutputLatency,
                'defaultSampleRate' :
                device_info.defaultSampleRate
                }

######################################################################
# Host Specific Stream Info
######################################################################

try:
    paMacCoreStreamInfo = pa.paMacCoreStreamInfo
except AttributeError:
    pass
else:
    class PaMacCoreStreamInfo:

        """
        Mac OS X-only: PaMacCoreStreamInfo is a PortAudio Host API
        Specific Stream Info data structure for specifying Mac OS
        X-only settings. Instantiate this class (if desired) and pass
        the instance as the argument in `PyAudio.open` to parameters
        ``input_host_api_specific_stream_info`` or
        ``output_host_api_specific_stream_info``. (See `Stream.__init__`.)

        :note: Mac OS X only.

        :group Flags (constants):
          paMacCoreChangeDeviceParameters, paMacCoreFailIfConversionRequired,
          paMacCoreConversionQualityMin, paMacCoreConversionQualityMedium,
          paMacCoreConversionQualityLow, paMacCoreConversionQualityHigh,
          paMacCoreConversionQualityMax, paMacCorePlayNice,
          paMacCorePro, paMacCoreMinimizeCPUButPlayNice, paMacCoreMinimizeCPU

        :group Settings:
          get_flags, get_channel_map

        """
        paMacCoreChangeDeviceParameters = pa.paMacCoreChangeDeviceParameters
        paMacCoreFailIfConversionRequired = pa.paMacCoreFailIfConversionRequired
        paMacCoreConversionQualityMin = pa.paMacCoreConversionQualityMin
        paMacCoreConversionQualityMedium = pa.paMacCoreConversionQualityMedium
        paMacCoreConversionQualityLow = pa.paMacCoreConversionQualityLow
        paMacCoreConversionQualityHigh = pa.paMacCoreConversionQualityHigh
        paMacCoreConversionQualityMax = pa.paMacCoreConversionQualityMax
        paMacCorePlayNice = pa.paMacCorePlayNice
        paMacCorePro = pa.paMacCorePro
        paMacCoreMinimizeCPUButPlayNice = pa.paMacCoreMinimizeCPUButPlayNice
        paMacCoreMinimizeCPU = pa.paMacCoreMinimizeCPU

        def __init__(self, flags = None, channel_map = None):
            """
            Initialize with flags and channel_map. See PortAudio
            documentation for more details on these parameters; they are
            passed almost verbatim to the PortAudio library.

            :param `flags`: paMacCore* flags OR'ed together.
                See `PaMacCoreStreamInfo`.
            :param `channel_map`: An array describing the channel mapping.
                See PortAudio documentation for usage.
            """

            kwargs = {"flags" : flags,
                      "channel_map" : channel_map}

            if flags == None:
                del kwargs["flags"]
            if channel_map == None:
                del kwargs["channel_map"]

            self._paMacCoreStreamInfo = paMacCoreStreamInfo(**kwargs)

        def get_flags(self):
            """
            Return the flags set at instantiation.

            :rtype: int
            """

            return self._paMacCoreStreamInfo.flags

        def get_channel_map(self):
            """
            Return the channel map set at instantiation.

            :rtype: tuple or None
            """

            return self._paMacCoreStreamInfo.channel_map

        def _get_host_api_stream_object(self):
            """ Private method. """

            return self._paMacCoreStreamInfo
