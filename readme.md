In order to take advantage of NMT metrics, you will need to enable a JVM
argument:

    -XX:NativeMemoryTracking=summary

If this JVM argument is not present, the NMT metrics will not be
tracked (but a warning will be logged via the `otj-jvm` library).
