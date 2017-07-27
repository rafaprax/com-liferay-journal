/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.journal.internal.upgrade.v1_1_3;

import com.liferay.petra.xml.XMLUtil;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONException;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.upgrade.UpgradeProcess;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.LoggingTimer;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.xml.Document;
import com.liferay.portal.kernel.xml.Element;
import com.liferay.portal.kernel.xml.SAXReaderUtil;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.List;
import java.util.Objects;

/**
 * @author Rafael Praxedes
 */
public class UpgradeJournalArticles extends UpgradeProcess {

	public UpgradeJournalArticles(
		CompanyLocalService companyLocalService, JSONFactory jsonFactory) {

		_companyLocalService = companyLocalService;
		_jsonFactory = jsonFactory;
	}

	@Override
	protected void doUpgrade() throws Exception {
		updateJournalArticles();
	}

	protected void transformFieldValues(List<Element> dynamicElementElements) {
		if ((dynamicElementElements == null) ||
			dynamicElementElements.isEmpty()) {

			return;
		}

		for (Element dynamicElementElement : dynamicElementElements) {
			String type = GetterUtil.getString(
				dynamicElementElement.attributeValue("type"));

			if (Objects.equals(type, "radio")) {
				List<Element> dynamicContentElements =
					dynamicElementElement.elements("dynamic-content");

				for (Element dynamicContentElement : dynamicContentElements) {
					transformRadioFieldValue(dynamicContentElement);
				}
			}

			List<Element> childDynamicElementElements =
				dynamicElementElement.elements("dynamic-element");

			transformFieldValues(childDynamicElementElements);
		}
	}

	protected String transformFieldValues(String content) throws Exception {
		if (content.indexOf(_TYPE_ATTRIBUTE_DDM_RADIO) == -1) {
			return content;
		}

		Document document = SAXReaderUtil.read(content);

		Element rootElement = document.getRootElement();

		List<Element> dynamicElementElements = rootElement.elements(
			"dynamic-element");

		transformFieldValues(dynamicElementElements);

		return XMLUtil.formatXML(document);
	}

	protected void transformRadioFieldValue(Element dynamicContentElement) {
		String value = dynamicContentElement.getText();

		if (Validator.isNull(value)) {
			return;
		}

		try {
			JSONArray jsonArray = _jsonFactory.createJSONArray(value);

			if (jsonArray.length() == 0) {
				value = StringPool.BLANK;
			}
			else {
				value = jsonArray.getString(0);
			}
		}
		catch (JSONException jsone) {
			if (_log.isWarnEnabled()) {
				_log.warn(jsone, jsone);
			}
		}

		dynamicContentElement.clearContent();

		dynamicContentElement.addCDATA(value);
	}

	protected void updateJournalArticles() throws Exception {
		try (LoggingTimer loggingTimer = new LoggingTimer()) {
			List<Company> companies = _companyLocalService.getCompanies();

			for (Company company : companies) {
				updateJournalArticles(company.getCompanyId());
			}
		}
	}

	protected void updateJournalArticles(long companyId) throws Exception {
		try (PreparedStatement ps1 = connection.prepareStatement(
				"select id_, content from JournalArticle where companyId = " +
					companyId);
			PreparedStatement ps2 = connection.prepareStatement(
				"update JournalArticle set content = ? where id_ = ?");
			ResultSet rs = ps1.executeQuery()) {

			while (rs.next()) {
				long id = rs.getLong("id_");
				String content = rs.getString("content");

				if (Validator.isNull(content)) {
					continue;
				}

				String updatedContent = transformFieldValues(content);

				if (!Objects.equals(content, updatedContent)) {
					ps2.setString(1, content);
					ps2.setLong(2, id);

					ps2.addBatch();
				}
			}

			ps2.executeBatch();
		}
	}

	private static final String _TYPE_ATTRIBUTE_DDM_RADIO = "type=\"radio\"";

	private static final Log _log = LogFactoryUtil.getLog(
		UpgradeJournalArticles.class);

	private final CompanyLocalService _companyLocalService;
	private final JSONFactory _jsonFactory;

}