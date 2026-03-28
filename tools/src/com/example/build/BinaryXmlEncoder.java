package com.example.build;

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Converts plain AndroidManifest.xml to Android binary XML format.
 * Parses arbitrary XML input — not hardcoded to a specific manifest structure.
 *
 * Attribute resource IDs are looked up from a built-in table of common
 * android:* attributes (extracted from android.R$attr).
 */
public class BinaryXmlEncoder {

    private static final short RES_XML_TYPE = 0x0003;
    private static final short RES_STRING_POOL_TYPE = 0x0001;
    private static final short RES_XML_RESOURCE_MAP_TYPE = 0x0180;
    private static final short RES_XML_START_NAMESPACE_TYPE = 0x0100;
    private static final short RES_XML_END_NAMESPACE_TYPE = 0x0101;
    private static final short RES_XML_START_ELEMENT_TYPE = 0x0102;
    private static final short RES_XML_END_ELEMENT_TYPE = 0x0103;

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    // Common android:* attribute name -> resource ID mapping
    // Extracted from android.R$attr (API 28)
    private static final Map<String, Integer> ATTR_RESOURCE_IDS = new HashMap<>();
    static {
        ATTR_RESOURCE_IDS.put("theme", 0x01010000);
        ATTR_RESOURCE_IDS.put("label", 0x01010001);
        ATTR_RESOURCE_IDS.put("icon", 0x01010002);
        ATTR_RESOURCE_IDS.put("name", 0x01010003);
        ATTR_RESOURCE_IDS.put("permission", 0x01010006);
        ATTR_RESOURCE_IDS.put("exported", 0x01010010);
        ATTR_RESOURCE_IDS.put("enabled", 0x0101000e);
        ATTR_RESOURCE_IDS.put("debuggable", 0x0101000f);
        ATTR_RESOURCE_IDS.put("description", 0x01010020);
        ATTR_RESOURCE_IDS.put("protectionLevel", 0x01010009);
        ATTR_RESOURCE_IDS.put("configChanges", 0x0101001f);
        ATTR_RESOURCE_IDS.put("screenOrientation", 0x0101001e);
        ATTR_RESOURCE_IDS.put("launchMode", 0x0101001d);
        ATTR_RESOURCE_IDS.put("value", 0x01010024);
        ATTR_RESOURCE_IDS.put("resource", 0x01010025);
        ATTR_RESOURCE_IDS.put("minSdkVersion", 0x0101020c);
        ATTR_RESOURCE_IDS.put("maxSdkVersion", 0x01010271);
        ATTR_RESOURCE_IDS.put("versionCode", 0x0101021b);
        ATTR_RESOURCE_IDS.put("versionName", 0x0101021c);
        ATTR_RESOURCE_IDS.put("targetSdkVersion", 0x01010270);
        ATTR_RESOURCE_IDS.put("allowBackup", 0x01010280);
        ATTR_RESOURCE_IDS.put("supportsRtl", 0x010103af);
        ATTR_RESOURCE_IDS.put("roundIcon", 0x0101052c);
        ATTR_RESOURCE_IDS.put("appComponentFactory", 0x0101057a);
        ATTR_RESOURCE_IDS.put("compileSdkVersion", 0x01010572);
        ATTR_RESOURCE_IDS.put("compileSdkVersionCodename", 0x01010573);
    }

    private final List<String> stringPool = new ArrayList<>();
    private final ByteArrayOutputStream body = new ByteArrayOutputStream();

    // Attribute names that need resource IDs, in order of their string pool index
    private final List<String> mappedAttrNames = new ArrayList<>();
    private final List<Integer> mappedAttrIds = new ArrayList<>();

    // Extra parameters injected into <manifest> and as <uses-sdk>
    private int minSdk = 21;
    private int targetSdk = 28;
    private int versionCode = 1;
    private String versionName = "1.0";

    private int getOrAddString(String s) {
        int idx = stringPool.indexOf(s);
        if (idx < 0) {
            idx = stringPool.size();
            stringPool.add(s);
        }
        return idx;
    }

    public byte[] encode(File xmlFile) throws Exception {
        // First pass: collect all attribute names that need resource IDs
        // and all strings, so we can build the string pool with mapped attrs first
        Set<String> attrNamesUsed = new LinkedHashSet<>();
        Set<String> allStrings = new LinkedHashSet<>();

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser parser = factory.newSAXParser();

        // Collect pass
        parser.parse(xmlFile, new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) {
                allStrings.add(localName);
                for (int i = 0; i < attributes.getLength(); i++) {
                    String attrUri = attributes.getURI(i);
                    String attrLocal = attributes.getLocalName(i);
                    String attrValue = attributes.getValue(i);
                    if (ANDROID_NS.equals(attrUri) && ATTR_RESOURCE_IDS.containsKey(attrLocal)) {
                        attrNamesUsed.add(attrLocal);
                    }
                    allStrings.add(attrLocal);
                    allStrings.add(attrValue);
                }
            }
        });

        // Also add injected attributes
        attrNamesUsed.add("versionCode");
        attrNamesUsed.add("versionName");
        attrNamesUsed.add("minSdkVersion");
        attrNamesUsed.add("targetSdkVersion");

        // Build string pool: resource-mapped attribute names first, sorted by resource ID
        List<Map.Entry<String, Integer>> sortedAttrs = new ArrayList<>();
        for (String name : attrNamesUsed) {
            Integer id = ATTR_RESOURCE_IDS.get(name);
            if (id != null) {
                sortedAttrs.add(new AbstractMap.SimpleEntry<>(name, id));
            }
        }
        sortedAttrs.sort(Comparator.comparingInt(Map.Entry::getValue));

        for (Map.Entry<String, Integer> entry : sortedAttrs) {
            int idx = getOrAddString(entry.getKey());
            mappedAttrNames.add(entry.getKey());
            mappedAttrIds.add(entry.getValue());
        }

        // Add remaining strings
        getOrAddString(ANDROID_NS);
        getOrAddString("android");
        getOrAddString("package");
        getOrAddString("uses-sdk");
        for (String s : allStrings) {
            getOrAddString(s);
        }
        getOrAddString(String.valueOf(versionCode));
        getOrAddString(versionName);

        int idxAndroidNs = stringPool.indexOf(ANDROID_NS);
        int idxAndroid = stringPool.indexOf("android");

        // Write namespace start
        writeStartNamespace(1, idxAndroid, idxAndroidNs);

        // Second pass: emit binary XML
        parser.parse(xmlFile, new EmitHandler(idxAndroidNs));

        // Write namespace end
        writeEndNamespace(99, idxAndroid, idxAndroidNs);

        // Assemble
        byte[] stringPoolBytes = encodeStringPool();
        byte[] resourceMapBytes = encodeResourceMap();
        byte[] bodyBytes = body.toByteArray();
        int totalSize = 8 + stringPoolBytes.length + resourceMapBytes.length + bodyBytes.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeShort(out, RES_XML_TYPE);
        writeShort(out, (short) 8);
        writeInt(out, totalSize);
        out.write(stringPoolBytes);
        out.write(resourceMapBytes);
        out.write(bodyBytes);
        return out.toByteArray();
    }

    private class EmitHandler extends DefaultHandler {
        private int line = 1;
        private final int idxAndroidNs;
        private boolean injectedUsesSdk = false;

        EmitHandler(int idxAndroidNs) {
            this.idxAndroidNs = idxAndroidNs;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            try {
                int nsIdx = -1; // elements don't have namespaces
                int nameIdx = getOrAddString(localName);

                // Collect attributes
                List<int[]> attrs = new ArrayList<>();
                for (int i = 0; i < attributes.getLength(); i++) {
                    String attrUri = attributes.getURI(i);
                    String attrLocal = attributes.getLocalName(i);
                    String attrValue = attributes.getValue(i);
                    int attrNsIdx = ANDROID_NS.equals(attrUri) ? idxAndroidNs : -1;
                    int attrNameIdx = getOrAddString(attrLocal);
                    addAttr(attrs, attrNsIdx, attrNameIdx, attrValue);
                }

                // Inject versionCode/versionName on <manifest>
                if ("manifest".equals(localName)) {
                    addAttr(attrs, idxAndroidNs, getOrAddString("versionCode"),
                            null, 0x10, versionCode);
                    addAttr(attrs, idxAndroidNs, getOrAddString("versionName"),
                            versionName);
                }

                writeStartElement(line, nsIdx, nameIdx, attrs);

                // Inject <uses-sdk> right after <manifest>
                if ("manifest".equals(localName) && !injectedUsesSdk) {
                    injectedUsesSdk = true;
                    line++;
                    List<int[]> sdkAttrs = new ArrayList<>();
                    addAttr(sdkAttrs, idxAndroidNs, getOrAddString("minSdkVersion"),
                            null, 0x10, minSdk);
                    addAttr(sdkAttrs, idxAndroidNs, getOrAddString("targetSdkVersion"),
                            null, 0x10, targetSdk);
                    int sdkNameIdx = getOrAddString("uses-sdk");
                    writeStartElement(line, -1, sdkNameIdx, sdkAttrs);
                    writeEndElement(line, -1, sdkNameIdx);
                }

                line++;
            } catch (IOException e) {
                throw new SAXException(e);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            try {
                writeEndElement(line, -1, getOrAddString(localName));
            } catch (IOException e) {
                throw new SAXException(e);
            }
        }

        private void addAttr(List<int[]> attrs, int nsIdx, int nameIdx, String value) {
            addAttr(attrs, nsIdx, nameIdx, value, -1, -1);
        }

        private void addAttr(List<int[]> attrs, int nsIdx, int nameIdx, String value,
                             int forceType, int forceData) {
            int rawValueIdx;
            int dataType;
            int data;

            if (forceType >= 0) {
                // Explicit type/data (integer, boolean, etc.)
                rawValueIdx = -1;
                dataType = forceType;
                data = forceData;
            } else if (value == null) {
                rawValueIdx = -1;
                dataType = 0x10; // TYPE_INT_DEC
                data = 0;
            } else if ("true".equals(value)) {
                rawValueIdx = -1;
                dataType = 0x12; // TYPE_INT_BOOLEAN
                data = 0xFFFFFFFF;
            } else if ("false".equals(value)) {
                rawValueIdx = -1;
                dataType = 0x12;
                data = 0;
            } else if (value.startsWith("0x")) {
                try {
                    rawValueIdx = -1;
                    dataType = 0x10; // TYPE_INT_DEC
                    data = Integer.parseUnsignedInt(value.substring(2), 16);
                } catch (NumberFormatException e) {
                    rawValueIdx = getOrAddString(value);
                    dataType = 0x03; // TYPE_STRING
                    data = rawValueIdx;
                }
            } else {
                try {
                    rawValueIdx = -1;
                    dataType = 0x10; // TYPE_INT_DEC
                    data = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    rawValueIdx = getOrAddString(value);
                    dataType = 0x03; // TYPE_STRING
                    data = rawValueIdx;
                }
            }

            attrs.add(new int[]{nsIdx, nameIdx, rawValueIdx, dataType, data});
        }
    }

    private byte[] encodeStringPool() throws IOException {
        int count = stringPool.size();
        ByteArrayOutputStream stringData = new ByteArrayOutputStream();
        int[] offsets = new int[count];
        for (int i = 0; i < count; i++) {
            offsets[i] = stringData.size();
            String s = stringPool.get(i);
            writeShort(stringData, (short) s.length());
            for (char c : s.toCharArray()) {
                writeShort(stringData, (short) c);
            }
            writeShort(stringData, (short) 0);
        }
        while (stringData.size() % 4 != 0) {
            stringData.write(0);
        }

        int headerSize = 28;
        int stringsStart = headerSize + count * 4;
        int totalSize = stringsStart + stringData.size();

        ByteArrayOutputStream pool = new ByteArrayOutputStream();
        writeShort(pool, RES_STRING_POOL_TYPE);
        writeShort(pool, (short) headerSize);
        writeInt(pool, totalSize);
        writeInt(pool, count);
        writeInt(pool, 0);
        writeInt(pool, 0); // flags: UTF-16
        writeInt(pool, stringsStart);
        writeInt(pool, 0);

        for (int offset : offsets) {
            writeInt(pool, offset);
        }
        pool.write(stringData.toByteArray());
        return pool.toByteArray();
    }

    private byte[] encodeResourceMap() throws IOException {
        ByteArrayOutputStream map = new ByteArrayOutputStream();
        int totalSize = 8 + mappedAttrIds.size() * 4;
        writeShort(map, RES_XML_RESOURCE_MAP_TYPE);
        writeShort(map, (short) 8);
        writeInt(map, totalSize);
        for (int id : mappedAttrIds) {
            writeInt(map, id);
        }
        return map.toByteArray();
    }

    private void writeStartNamespace(int lineNumber, int prefix, int uri) throws IOException {
        writeShort(body, RES_XML_START_NAMESPACE_TYPE);
        writeShort(body, (short) 16);
        writeInt(body, 24);
        writeInt(body, lineNumber);
        writeInt(body, -1);
        writeInt(body, prefix);
        writeInt(body, uri);
    }

    private void writeEndNamespace(int lineNumber, int prefix, int uri) throws IOException {
        writeShort(body, RES_XML_END_NAMESPACE_TYPE);
        writeShort(body, (short) 16);
        writeInt(body, 24);
        writeInt(body, lineNumber);
        writeInt(body, -1);
        writeInt(body, prefix);
        writeInt(body, uri);
    }

    private void writeStartElement(int lineNumber, int nsIdx, int nameIdx, List<int[]> attrs) throws IOException {
        int attrCount = attrs.size();
        int chunkSize = 36 + attrCount * 20;

        writeShort(body, RES_XML_START_ELEMENT_TYPE);
        writeShort(body, (short) 16);
        writeInt(body, chunkSize);
        writeInt(body, lineNumber);
        writeInt(body, -1);
        writeInt(body, nsIdx);
        writeInt(body, nameIdx);
        writeShort(body, (short) 0x14);
        writeShort(body, (short) 0x14);
        writeShort(body, (short) attrCount);
        writeShort(body, (short) 0);
        writeShort(body, (short) 0);
        writeShort(body, (short) 0);

        for (int[] attr : attrs) {
            writeInt(body, attr[0]); // ns
            writeInt(body, attr[1]); // name
            writeInt(body, attr[2]); // rawValue
            writeShort(body, (short) 8);
            body.write(0);
            body.write(attr[3]); // dataType
            writeInt(body, attr[4]); // data
        }
    }

    private void writeEndElement(int lineNumber, int nsIdx, int nameIdx) throws IOException {
        writeShort(body, RES_XML_END_ELEMENT_TYPE);
        writeShort(body, (short) 16);
        writeInt(body, 24);
        writeInt(body, lineNumber);
        writeInt(body, -1);
        writeInt(body, nsIdx);
        writeInt(body, nameIdx);
    }

    private static void writeShort(OutputStream out, short v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static void writeInt(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: BinaryXmlEncoder <input.xml> <output> [minSdk] [targetSdk] [versionCode] [versionName]");
            System.exit(1);
        }
        File inputFile = new File(args[0]);
        String outputFile = args[1];
        BinaryXmlEncoder encoder = new BinaryXmlEncoder();
        if (args.length > 2) encoder.minSdk = Integer.parseInt(args[2]);
        if (args.length > 3) encoder.targetSdk = Integer.parseInt(args[3]);
        if (args.length > 4) encoder.versionCode = Integer.parseInt(args[4]);
        if (args.length > 5) encoder.versionName = args[5];

        byte[] data = encoder.encode(inputFile);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(data);
        }
        System.out.println("Wrote binary manifest: " + outputFile + " (" + data.length + " bytes)");
    }
}
