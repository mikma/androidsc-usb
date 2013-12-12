
CCID_PATH = jni/ccid
CCID_VERSION = $(shell cat $(CCID_PATH)/configure.ac | grep ^AC_INIT | sed -e 's/AC_INIT(\[ccid\],\[\(.*\)\])/\1/' )
SUPPORTED_READERS_TXT = $(CCID_PATH)/readers/supported_readers.txt
INFO_PLIST_SRC = $(CCID_PATH)/src/Info.plist.src
INFO_PLIST = assets/Info.plist


all: $(INFO_PLIST)

clean:
	$(RM) $(INFO_PLIST)

$(INFO_PLIST): $(SUPPORTED_READERS_TXT) $(INFO_PLIST_SRC)
	$(CCID_PATH)/src/create_Info_plist.pl $(SUPPORTED_READERS_TXT) $(INFO_PLIST_SRC) --target=libccid.so --version=$(CCID_VERSION) > $@
