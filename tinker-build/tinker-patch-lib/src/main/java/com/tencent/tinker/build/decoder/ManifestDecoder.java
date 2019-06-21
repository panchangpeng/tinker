/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.build.decoder;


import com.tencent.tinker.build.apkparser.AndroidParser;
import com.tencent.tinker.build.apkparser.Component;
import com.tencent.tinker.build.info.InfoWriter;
import com.tencent.tinker.build.patch.Configuration;
import com.tencent.tinker.build.util.Logger;
import com.tencent.tinker.build.util.TinkerPatchException;
import com.tencent.tinker.build.util.TypedValue;
import com.tencent.tinker.build.util.Utils;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.XMLWriter;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Created by zhangshaowen on 16/4/6.
 */

public class ManifestDecoder extends BaseDecoder {
    private static final String XML_NODENAME_APPLICATION        = "application";
    private static final String XML_NODENAME_USES_SDK           = "uses-sdk";
    private static final String XML_NODEATTR_MIN_SDK_VERSION    = "minSdkVersion";
    private static final String XML_NODEATTR_TARGET_SDK_VERSION = "targetSdkVersion";
    private static final String XML_NODEATTR_PACKAGE            = "package";
    private static final String XML_NODENAME_ACTIVITY           = "activity";
    private static final String XML_NODENAME_SERVICE            = "service";
    private static final String XML_NODENAME_RECEIVER           = "receiver";
    private static final String XML_NODENAME_PROVIDER           = "provider";
    private static final String XML_NODEATTR_NAME               = "name";
    private static final String XML_NODEATTR_EXPORTED           = "exported";
    private static final String XML_NODEATTR_PROCESS            = "process";
    private static final String XML_NODENAME_INTENTFILTER       = "intent-filter";

    private final InfoWriter logWriter;

    public ManifestDecoder(Configuration config, String metaPath, String logPath) throws IOException {
        super(config);

        if (logPath != null) {
            logWriter = new InfoWriter(config, config.mOutFolder + File.separator + logPath);
        } else {
            logWriter = null;
        }
    }

    @Override
    public boolean patch(File oldFile, File newFile) throws IOException, TinkerPatchException {
        try {
            AndroidParser oldAndroidManifest = AndroidParser.getAndroidManifest(oldFile);
            AndroidParser newAndroidManifest = AndroidParser.getAndroidManifest(newFile);

            //check minSdkVersion
            int oldMinSdkVersion = Integer.parseInt(oldAndroidManifest.apkMeta.getMinSdkVersion());

            final boolean oldMinSdkVersionSupportRawMode = oldMinSdkVersion >= TypedValue.ANDROID_40_API_LEVEL;
            if (!oldMinSdkVersionSupportRawMode && config.isHotpatch()) {
                if (config.mDexRaw) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("your old apk's minSdkVersion ")
                      .append(oldMinSdkVersion)
                      .append(" is below 14, you should set the dexMode to 'jar', ")
                      .append("otherwise, it will crash at some time");
                    announceWarningOrException(sb.toString());
                    return false;
                }
            }

            final String oldXml = oldAndroidManifest.xml.trim();
            final String newXml = newAndroidManifest.xml.trim();
            final boolean isManifestChanged = !oldXml.equals(newXml);

            if (!isManifestChanged) {
                Logger.d("\nManifest has no changes, skip rest decode works.");
                return false;
            }

            // check whether there is any new Android Component and get their names.
            // so far only Activity increment can pass checking.
            final Set<Component> incActivities = getIncrementActivities(oldAndroidManifest.activities, newAndroidManifest.activities);
            final Set<Component> incServices = getIncrementServices(oldAndroidManifest.services, newAndroidManifest.services);
            final Set<Component> incReceivers = getIncrementReceivers(oldAndroidManifest.receivers, newAndroidManifest.receivers);
            final Set<Component> incProviders = getIncrementProviders(oldAndroidManifest.providers, newAndroidManifest.providers);

            final boolean hasIncComponent = (!incActivities.isEmpty() || !incServices.isEmpty()
                    || !incProviders.isEmpty() || !incReceivers.isEmpty());
            final boolean hasIncExportedComponent = (hasExportedComponent(incActivities) || hasExportedComponent(incServices)
                    || hasExportedComponent(incReceivers) || hasExportedComponent(incProviders));

            if (config.isHotpatch()) {
                if (hasIncExportedComponent) {
                    announceWarningOrException("manifest was changed, while hot plug component can not support export component. "
                            + "Such changes will not take effect.");
                    return false;
                } else if (!config.mSupportHotplugComponent) {
                    announceWarningOrException("manifest was changed, while hot plug component support mode is disabled. "
                            + "Such changes will not take effect.");
                    return false;
                } else {
                    Logger.d("start write inc component for hot plug.");
                }
            } else if (config.isTKDIff()) {
                Logger.d("ignore to write inc component for tk diff.");
            } else {
                announceWarningOrException(String.format("unsupported packing mode %s", config.mPackingMode));
                return false;
            }

            Logger.d(String.format("start write inc component meta. hasIncComponent:%b",hasIncComponent));
            // generate increment manifest.
            if (hasIncComponent && config.isHotpatch()) {
                final Document newXmlDoc = DocumentHelper.parseText(newAndroidManifest.xml);
                final Document incXmlDoc = DocumentHelper.createDocument();

                final Element newRootNode = newXmlDoc.getRootElement();
                final String packageName = newRootNode.attributeValue(XML_NODEATTR_PACKAGE);
                if (Utils.isNullOrNil(packageName)) {
                    throw new TinkerPatchException("Unable to find package name from manifest: " + newFile.getAbsolutePath());
                }

                final Element newAppNode = newRootNode.element(XML_NODENAME_APPLICATION);

                final Element incAppNode = incXmlDoc.addElement(newAppNode.getQName());
                copyAttributes(newAppNode, incAppNode);

                if (!incActivities.isEmpty()) {
                    final List<Element> newActivityNodes = newAppNode.elements(XML_NODENAME_ACTIVITY);
                    final List<Element> incActivityNodes = getIncrementActivityNodes(packageName, newActivityNodes, incActivities);
                    Logger.d(String.format("newActivityNodes size:%d  incActivities size:%d incActivityNodes size:%d", newActivityNodes.size(), incActivities.size(), incActivityNodes.size()));
                    for (Element node : incActivityNodes) {
                        incAppNode.add(node.detach());
                        Logger.d(node.detach().asXML());
                    }
                }

                if (!incServices.isEmpty()) {
                    final List<Element> newServiceNodes = newAppNode.elements(XML_NODENAME_SERVICE);
                    final List<Element> incServiceNodes = getIncrementServiceNodes(packageName, newServiceNodes, incServices);
                    for (Element node : incServiceNodes) {
                        incAppNode.add(node.detach());
                    }
                }

                if (!incReceivers.isEmpty()) {
                    final List<Element> newReceiverNodes = newAppNode.elements(XML_NODENAME_RECEIVER);
                    final List<Element> incReceiverNodes = getIncrementReceiverNodes(packageName, newReceiverNodes, incReceivers);
                    for (Element node : incReceiverNodes) {
                        incAppNode.add(node.detach());
                    }
                }

                if (!incProviders.isEmpty()) {
                    final List<Element> newProviderNodes = newAppNode.elements(XML_NODENAME_PROVIDER);
                    final List<Element> incProviderNodes = getIncrementProviderNodes(packageName, newProviderNodes, incProviders);
                    for (Element node : incProviderNodes) {
                        incAppNode.add(node.detach());
                    }
                }

                final File incXmlOutput = new File(config.mTempResultDir, TypedValue.INCCOMPONENT_META_FILE);
                if (!incXmlOutput.exists()) {
                    incXmlOutput.getParentFile().mkdirs();
                }
                OutputStream os = null;
                try {
                    os = new BufferedOutputStream(new FileOutputStream(incXmlOutput));
                    final XMLWriter docWriter = new XMLWriter(os);
                    docWriter.write(incXmlDoc);
                    docWriter.close();
                } finally {
                    Utils.closeQuietly(os);
                }
            }

            if (isManifestChanged && !hasIncComponent) {
                Logger.d("\nManifest was changed, while there's no any new components added."
                        + " Make sure if such changes were all you expected.\n");
            }
        } catch (ParseException e) {
            e.printStackTrace();
            throw new TinkerPatchException("Parse android manifest error!");
        } catch (DocumentException e) {
            e.printStackTrace();
            throw new TinkerPatchException("Parse android manifest by dom4j error!");
        } catch (IOException e) {
            e.printStackTrace();
            throw new TinkerPatchException("Failed to generate increment manifest.", e);
        }
        return false;
    }

    private Set<Component> getIncrementActivities(Collection<Component> oldActivities, Collection<Component> newActivities) {
        final Set<Component> incNames = new HashSet<>(newActivities);
        incNames.removeAll(oldActivities);
        return incNames;
    }

    private Set<Component> getIncrementServices(Collection<Component> oldServices, Collection<Component> newServices) {
        final Set<Component> incNames = new HashSet<>(newServices);
        incNames.removeAll(oldServices);
        if (!incNames.isEmpty()) {
            announceWarningOrException("found added services: " + incNames.toString()
                    + "\n currently tinker does not support increase new services, "
                    + "such these changes would not take effect.");
        }
        return incNames;
    }

    private Set<Component> getIncrementReceivers(Collection<Component> oldReceivers, Collection<Component> newReceivers) {
        final Set<Component> incNames = new HashSet<>(newReceivers);
        incNames.removeAll(oldReceivers);
        if (!incNames.isEmpty()) {
            announceWarningOrException("found added receivers: " + incNames.toString()
                    + "\n currently tinker does not support increase new receivers, "
                    + "such these changes would not take effect.");
        }
        return incNames;
    }

    private Set<Component> getIncrementProviders(Collection<Component> oldProviders, Collection<Component> newProviders) {
        final Set<Component> incNames = new HashSet<>(newProviders);
        incNames.removeAll(oldProviders);
        if (!incNames.isEmpty()) {
            announceWarningOrException("found added providers: " + incNames.toString()
                    + "\n currently tinker does not support increase new providers, "
                    + "such these changes would not take effect.");
        }
        return incNames;
    }

    private List<Element> getIncrementActivityNodes(String packageName, List<Element> newActivityNodes, Collection<Component> incActivities) {
        final List<Element> result = new ArrayList<>();
        for (Element newActivityNode : newActivityNodes) {
            String activityClazzName = newActivityNode.attributeValue(XML_NODEATTR_NAME);
            if (activityClazzName.charAt(0) == '.') {
                activityClazzName = packageName + activityClazzName;
            }
            if (!incActivities.contains(new Component(activityClazzName, com.tencent.tinker.build.apkparser.AndroidParser.TYPE_ACTIVITY, false))) {
                continue;
            }
            final String exportedVal = newActivityNode.attributeValue(XML_NODEATTR_EXPORTED,
                    Utils.isNullOrNil(newActivityNode.elements(XML_NODENAME_INTENTFILTER)) ? "false" : "true");
            if ("true".equalsIgnoreCase(exportedVal)) {
                announceWarningOrException(
                        String.format("found a new exported activity %s"
                                + ", tinker does not support increase exported activity.", activityClazzName)
                );
            }
            final String processVal = newActivityNode.attributeValue(XML_NODEATTR_PROCESS);
            if (processVal != null && processVal.charAt(0) == ':') {
                announceWarningOrException(
                        String.format("found a new activity %s which would be run in standalone process"
                                + ", tinker does not support increase such kind of activities.", activityClazzName)
                );
            }

            Logger.d("Found increment activity: " + activityClazzName);

            result.add(newActivityNode);
        }
        return result;
    }

    private List<Element> getIncrementServiceNodes(String packageName, List<Element> newServiceNodes, Collection<Component> incServices) {
        announceWarningOrException("currently tinker does not support increase new services.");
        return Collections.emptyList();
    }

    private List<Element> getIncrementReceiverNodes(String packageName, List<Element> newReceiverNodes, Collection<Component> incReceivers) {
        announceWarningOrException("currently tinker does not support increase new receivers.");
        return Collections.emptyList();
    }

    private List<Element> getIncrementProviderNodes(String packageName, List<Element> newProviderNodes, Collection<Component> incProviders) {
        announceWarningOrException("currently tinker does not support increase new providers.");
        return Collections.emptyList();
    }

    private boolean hasExportedComponent(Collection<Component> components) {
        if (components != null && !components.isEmpty()) {
            for (Component c : components) {
                if (c.isExported()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void copyAttributes(Element srcNode, Element destNode) {
        for (Object attrObj : srcNode.attributes()) {
            final Attribute attr = (Attribute) attrObj;
            destNode.addAttribute(attr.getQName(), attr.getValue());
        }
    }

    private void announceWarningOrException(String message) {
        if (config.mIgnoreWarning) {
            final String msg = "Warning:ignoreWarning is true, but " + message;
            Logger.e(msg);
        } else {
            final String msg = "Warning:ignoreWarning is false, " + message;
            Logger.e(msg);
            throw new TinkerPatchException(msg);
        }
    }

    @Override
    public void onAllPatchesStart() throws IOException, TinkerPatchException {

    }

    @Override
    public void onAllPatchesEnd() throws IOException, TinkerPatchException {

    }

    @Override
    protected void clean() {
        if (logWriter != null) {
            logWriter.close();
        }
    }
}
