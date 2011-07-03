/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.thefind;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import javolution.util.FastList;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.StringUtil;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.transaction.GenericTransactionException;
import org.ofbiz.entity.transaction.TransactionUtil;
import org.ofbiz.entity.util.EntityListIterator;
import org.ofbiz.product.product.ProductSearchEvents;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ModelService;
import org.ofbiz.service.ServiceUtil;

public class TheFindSearchEvents {

    public static final String module = TheFindSearchEvents.class.getName();
    public static final String resource = "TheFindUiLabels";
    public static final int DEFAULT_TX_TIMEOUT = 600;

    public static String searchExportProductListToThefind(HttpServletRequest request, HttpServletResponse response) {
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        Locale locale = UtilHttp.getLocale(request);
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        String selectResult = request.getParameter("selectResult");
        List<String> productExportList = FastList.newInstance();
        String errMsg = null;

        try {
            boolean beganTransaction = TransactionUtil.begin(DEFAULT_TX_TIMEOUT);
            try {
                if (UtilValidate.isEmpty(selectResult)) {
                    // If the passed list of product ids is empty, get the list from the search parameters in the request
                    EntityListIterator eli = ProductSearchEvents.getProductSearchResults(request);
                    if (eli == null) {
                        errMsg = UtilProperties.getMessage(resource,"thefindsearchevents.no_results_found_probably_error_constraints", UtilHttp.getLocale(request));
                        Debug.logError(errMsg, module);
                        request.setAttribute("_ERROR_MESSAGE_", errMsg);
                        return "error";
                    }

                    GenericValue searchResultView = null;
                    while ((searchResultView = eli.next()) != null) {
                        productExportList.add(searchResultView.getString("mainProductId"));
                    }
                    eli.close();
                } else {
                    if (selectResult.startsWith("[")) {
                        productExportList = StringUtil.toList(selectResult);
                    } else {
                        if (selectResult.startsWith("{")) {
                            selectResult = selectResult.replace("{", "[");
                            selectResult = selectResult.replace("}", "]");
                            productExportList = StringUtil.toList(selectResult);
                        } else {
                            productExportList.add(selectResult);
                        }
                    }
                }
                String webSiteUrl = request.getParameter("webSiteUrl");
                String imageUrl = request.getParameter("imageUrl");
                String countryCode = request.getParameter("countryCode");
                String productStoreId = request.getParameter("productStoreId");
                String webSiteMountPoint = request.getParameter("webSiteMountPoint");

                // Export all or selected products to TheFind
                try {
                    Map<String, Object> inMap = UtilMisc.toMap("selectResult", productExportList,
                                               "webSiteUrl", webSiteUrl,
                                               "webSiteMountPoint", webSiteMountPoint,
                                               "imageUrl", imageUrl,
                                               "countryCode", countryCode);
                    inMap.put("userLogin", userLogin);
                    inMap.put("productStoreId", productStoreId);
                    Map<String, Object> exportResult = dispatcher.runSync("exportProductsToTheFind", inMap);
                    if (ServiceUtil.isError(exportResult)) {
                        List<String> errorMessages = UtilGenerics.checkList(exportResult.get(ModelService.ERROR_MESSAGE_LIST), String.class);
                        if (UtilValidate.isNotEmpty(errorMessages)) {
                            request.setAttribute("_ERROR_MESSAGE_LIST_", errorMessages);
                        } else {
                            request.setAttribute("_ERROR_MESSAGE_", ServiceUtil.getErrorMessage(exportResult));
                        }
                        return "error";
                    } else if (ServiceUtil.isFailure(exportResult)) {
                        List<String> eventMessages = UtilGenerics.checkList(exportResult.get(ModelService.ERROR_MESSAGE_LIST), String.class);
                        if (UtilValidate.isNotEmpty(eventMessages)) {
                            request.setAttribute("_EVENT_MESSAGE_LIST_", eventMessages);
                        } else {
                            request.setAttribute("_EVENT_MESSAGE_", ServiceUtil.getErrorMessage(exportResult));
                        }
                    } else {
                        String successMessage = UtilProperties.getMessage(resource, "thefindsearchevents.successMessage", locale);
                        request.setAttribute("_EVENT_MESSAGE_", successMessage);
                    }
                } catch (GenericServiceException e) {
                    errMsg = UtilProperties.getMessage(resource, "thefindsearchevents.exceptionCallingExportToThefind", locale);
                    Debug.logError(e, errMsg, module);
                    request.setAttribute("_ERROR_MESSAGE_", errMsg);
                    return "error";
                }
            } catch (GenericEntityException e) {
                errMsg = UtilProperties.getMessage(resource, "thefindsearchevents.error_getting_search_results", locale);
                Debug.logError(e, errMsg, module);
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
                return "error";
            } finally {
                TransactionUtil.commit(beganTransaction);
            }
        } catch (GenericTransactionException e) {
            errMsg = UtilProperties.getMessage(resource, "thefindsearchevents.error_getting_search_results", locale);
            Debug.logError(e, errMsg, module);
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }
        return "success";
    }
}