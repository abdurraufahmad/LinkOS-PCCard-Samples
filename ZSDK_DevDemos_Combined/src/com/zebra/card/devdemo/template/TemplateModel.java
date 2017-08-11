/***********************************************
 * CONFIDENTIAL AND PROPRIETARY
 * 
 * The source code and other information contained herein is the confidential and exclusive property of
 * ZIH Corp. and is subject to the terms and conditions in your end user license agreement.
 * This source code, and any other information contained herein, shall not be copied, reproduced, published, 
 * displayed or distributed, in whole or in part, in any medium, by any means, for any purpose except as
 * expressly permitted under such license agreement.
 * 
 * Copyright ZIH Corp. 2016
 * 
 * ALL RIGHTS RESERVED
 ***********************************************/

package com.zebra.card.devdemo.template;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.swing.JTextArea;

import org.apache.commons.io.FileUtils;

import com.zebra.card.devdemo.DiscoveredPrinterForDevDemo;
import com.zebra.card.devdemo.PrinterModel;
import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.common.card.containers.GraphicsInfo;
import com.zebra.sdk.common.card.containers.TemplateJob;
import com.zebra.sdk.common.card.enumerations.CardDestination;
import com.zebra.sdk.common.card.exceptions.ZebraCardException;
import com.zebra.sdk.common.card.jobSettings.ZebraCardJobSettingNames;
import com.zebra.sdk.common.card.printer.ZebraCardPrinter;
import com.zebra.sdk.common.card.printer.ZebraCardPrinterFactory;
import com.zebra.sdk.common.card.template.ZebraCardTemplate;
import com.zebra.sdk.settings.SettingsException;

public class TemplateModel extends PrinterModel {

	private final String templateFilePath;
	private final String imageDirectory;
	private final String templateName;

	private final JTextArea statusTextArea;

	private final ZebraCardTemplate zebraCardTemplate;

	public TemplateModel(String templateFilePath, String imageDirectory, JTextArea statusTextArea) throws IOException, IllegalArgumentException, ZebraCardException {
		super();

		this.templateFilePath = templateFilePath;
		this.imageDirectory = imageDirectory;
		this.statusTextArea = statusTextArea;

		zebraCardTemplate = new ZebraCardTemplate(null);

		String templateFileName = new File(templateFilePath).getName();
		templateName = removeFileExtension(templateFileName);
		saveTemplateFile(templateFilePath);
	}

	private void saveTemplateFile(String templateFilePath) throws IOException, IllegalArgumentException, ZebraCardException {
		if (templateFilePath != null) {
			if (templateName == null) {
				throw new IllegalArgumentException("No template name was found for " + this.templateFilePath + ".");
			}

			verboseFormatPrint("Reading template file %s%n", templateFilePath);
			String templateData = FileUtils.readFileToString(new File(templateFilePath));

			List<String> storedTemplateNames = zebraCardTemplate.getAllTemplateNames();
			if (storedTemplateNames.contains(templateName)) {
				zebraCardTemplate.deleteTemplate(templateName);
			}

			verboseFormatPrint("Saving template %s%n", templateName);
			zebraCardTemplate.saveTemplate(templateName, templateData);

			String imageFileDirectory = imageDirectory.isEmpty() ? new File(templateFilePath).getParent() : this.imageDirectory;
			List<String> existingTemplateImageFiles = zebraCardTemplate.getAllTemplateImageNames();
			File[] allImageFiles = getImageFilesInDirectory(imageFileDirectory);

			for (File imageFile : allImageFiles) {
				String templateImageName = imageFile.getName();
				if (existingTemplateImageFiles.contains(templateImageName)) {
					zebraCardTemplate.deleteTemplateImage(templateImageName);
				}

				verboseFormatPrint("Reading image file %s%n", imageFile.toString());
				byte[] templateImageData = FileUtils.readFileToByteArray(imageFile);

				verboseFormatPrint("Saving image file with name '%s'%n", templateImageName);
				zebraCardTemplate.saveTemplateImage(templateImageName, templateImageData);
			}
		} else {
			throw new IllegalArgumentException("Must specify a template or image file path");
		}
	}

	public List<String> getTemplateFields() throws IllegalArgumentException, ZebraCardException, IOException {
		return zebraCardTemplate.getTemplateFields(templateName);
	}

	private File[] getImageFilesInDirectory(String directoryPath) {
		File[] imageFiles = new File(directoryPath).listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".bmp");
			}
		});
		return imageFiles;
	}

	protected void verboseFormatPrint(String format, Object... args) {
		statusTextArea.append(String.format(format, args));
	}

	protected void verbosePrint(String format) {
		statusTextArea.append(format);
	}

	private String removeFileExtension(String fileName) {
		return fileName.split("\\.")[0];
	}

	public List<GraphicsInfo> generatePreview(Map<String, String> variableData) throws IllegalArgumentException, IOException, ConnectionException, SettingsException, ZebraCardException {
		String templateData = zebraCardTemplate.getTemplate(templateName);
		TemplateJob templateJob = generateTemplateJob(templateName, zebraCardTemplate, templateData, variableData);
		return templateJob.graphicsData;
	}

	public void print(DiscoveredPrinterForDevDemo discoveredPrinter, Map<String, String> variableData, JTextArea jobStatusArea)
			throws IllegalArgumentException, IOException, ConnectionException, SettingsException, ZebraCardException {
		ZebraCardPrinter zebraCardPrinter = null;
		Connection connection = null;

		try {
			String templateData = zebraCardTemplate.getTemplate(templateName);
			TemplateJob templateJob = generateTemplateJob(templateName, zebraCardTemplate, templateData, variableData);

			connection = discoveredPrinter.getConnection();
			connection.open();

			zebraCardPrinter = ZebraCardPrinterFactory.getInstance(connection);

			boolean isDestinationValid = zebraCardPrinter.isJobSettingValid(ZebraCardJobSettingNames.CARD_DESTINATION, CardDestination.Eject.name());
			if (!isDestinationValid) {
				zebraCardPrinter.setJobSetting(ZebraCardJobSettingNames.CARD_DESTINATION, CardDestination.LaminatorAny.name());
			}

			int jobId = zebraCardPrinter.printTemplate(1, templateJob);
			verboseFormatPrint("Received job id value of %d%n", jobId);

			pollJobStatus(zebraCardPrinter, jobId, jobStatusArea);
		} finally {
			cleanUpQuietly(zebraCardPrinter, connection);
		}
	}

	private TemplateJob generateTemplateJob(String templateFileName, ZebraCardTemplate zebraCardTemplate, String templateData, Map<String, String> fieldDataMap)
			throws ConnectionException, SettingsException, ZebraCardException, IOException {
		if (templateFileName != null) {
			verboseFormatPrint("Generating print job from template %s%n", templateFileName);
			return zebraCardTemplate.generateTemplateJob(templateFileName, fieldDataMap);
		} else {
			throw new IllegalArgumentException("Must specify a template file name or template data");
		}
	}
}
