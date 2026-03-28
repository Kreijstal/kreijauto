API_LEVEL ?= 28
ANDROID_JAR := vendor/android-platforms/android-$(API_LEVEL)/android.jar

DX_SRC := vendor/dalvik-dx/platform_dalvik/dx/src
APKSIG_SRC := vendor/apksig/src/main/java
APKSIGNER_SRC := vendor/apksig/src/apksigner/java
TOOLS_SRC := tools/src

APP_DIR := app
SRC_DIR := $(APP_DIR)/src
MANIFEST := $(APP_DIR)/AndroidManifest.xml

BUILD_DIR := build
CLASSES_DIR := $(BUILD_DIR)/classes
DX_CLASSES_DIR := $(BUILD_DIR)/dx
DX_JAR := $(BUILD_DIR)/dx.jar
APKSIG_CLASSES_DIR := $(BUILD_DIR)/apksig
APKSIGNER_JAR := $(BUILD_DIR)/apksigner.jar
TOOLS_CLASSES_DIR := $(BUILD_DIR)/tools

PACKAGE := com.example.helloworld
APP_LABEL := Hello World
ACTIVITY := .MainActivity
MIN_SDK ?= 21
TARGET_SDK ?= 28
VERSION_CODE ?= 1
VERSION_NAME ?= 1.0

SOURCES := $(shell find $(SRC_DIR) -name '*.java')
DX_SOURCES := $(shell find $(DX_SRC) -name '*.java')
APKSIG_SOURCES := $(shell find $(APKSIG_SRC) -name '*.java' ! -path '*/kms/aws/*' ! -path '*/kms/gcp/*')
APKSIGNER_SOURCES := $(shell find $(APKSIGNER_SRC) -name '*.java')
TOOLS_SOURCES := $(shell find $(TOOLS_SRC) -name '*.java')

KEYSTORE := $(BUILD_DIR)/debug.keystore
KS_PASS := android

.PHONY: all clean

all: $(BUILD_DIR)/app.apk

# Build dx from source
$(DX_JAR): $(DX_SOURCES)
	mkdir -p $(DX_CLASSES_DIR)
	javac -source 8 -target 8 -d $(DX_CLASSES_DIR) $(DX_SOURCES)
	jar cf $(DX_JAR) -C $(DX_CLASSES_DIR) .

# Build apksigner from source
$(APKSIGNER_JAR): $(APKSIG_SOURCES) $(APKSIGNER_SOURCES)
	mkdir -p $(APKSIG_CLASSES_DIR)
	javac -source 8 -target 8 -d $(APKSIG_CLASSES_DIR) $(APKSIG_SOURCES)
	printf 'package org.conscrypt;\npublic class OpenSSLProvider extends java.security.Provider {\n    public OpenSSLProvider() { super("Conscrypt", 1.0, "stub"); }\n}\n' > $(BUILD_DIR)/OpenSSLProvider.java
	javac -source 8 -target 8 -d $(APKSIG_CLASSES_DIR) $(BUILD_DIR)/OpenSSLProvider.java
	javac -source 8 -target 8 -cp $(APKSIG_CLASSES_DIR) -d $(APKSIG_CLASSES_DIR) $(APKSIGNER_SOURCES)
	cp $(APKSIGNER_SRC)/com/android/apksigner/*.txt $(APKSIG_CLASSES_DIR)/com/android/apksigner/
	jar cf $(APKSIGNER_JAR) -C $(APKSIG_CLASSES_DIR) .

# Build our build tools
$(TOOLS_CLASSES_DIR)/com/example/build/ApkBuilder.class: $(TOOLS_SOURCES)
	mkdir -p $(TOOLS_CLASSES_DIR)
	javac -source 8 -target 8 -d $(TOOLS_CLASSES_DIR) $(TOOLS_SOURCES)

# Compile app Java sources
$(CLASSES_DIR)/com/example/helloworld/MainActivity.class: $(SOURCES) $(ANDROID_JAR)
	mkdir -p $(CLASSES_DIR)
	javac -source 8 -target 8 \
		-bootclasspath $(ANDROID_JAR) \
		-classpath $(ANDROID_JAR) \
		-d $(CLASSES_DIR) \
		$(SOURCES)

# Convert .class to .dex
$(BUILD_DIR)/classes.dex: $(CLASSES_DIR)/com/example/helloworld/MainActivity.class $(DX_JAR)
	java -cp $(DX_JAR) com.android.dx.command.Main --dex --output=$@ $(CLASSES_DIR)

# Encode AndroidManifest.xml to binary format
$(BUILD_DIR)/AndroidManifest.xml: $(MANIFEST) $(TOOLS_CLASSES_DIR)/com/example/build/ApkBuilder.class
	java -cp $(TOOLS_CLASSES_DIR) com.example.build.BinaryXmlEncoder \
		$(MANIFEST) $@ $(MIN_SDK) $(TARGET_SDK) $(VERSION_CODE) $(VERSION_NAME)

# Package unsigned APK
$(BUILD_DIR)/app-unsigned.apk: $(BUILD_DIR)/AndroidManifest.xml $(BUILD_DIR)/classes.dex $(TOOLS_CLASSES_DIR)/com/example/build/ApkBuilder.class
	java -cp $(TOOLS_CLASSES_DIR) com.example.build.ApkBuilder \
		$(BUILD_DIR)/AndroidManifest.xml $(BUILD_DIR)/classes.dex $@

# Generate debug keystore
$(KEYSTORE):
	keytool -genkeypair -keystore $@ -alias debug -keyalg RSA -keysize 2048 \
		-validity 10000 -storepass $(KS_PASS) -keypass $(KS_PASS) -dname "CN=Debug"

# Sign APK (v1 + v2 + v3)
$(BUILD_DIR)/app.apk: $(BUILD_DIR)/app-unsigned.apk $(APKSIGNER_JAR) $(KEYSTORE)
	java -cp $(APKSIGNER_JAR) com.android.apksigner.ApkSignerTool sign \
		--ks $(KEYSTORE) --ks-pass pass:$(KS_PASS) --key-pass pass:$(KS_PASS) \
		--out $@ $<

clean:
	rm -rf $(BUILD_DIR)
