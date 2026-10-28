package com.example.parser

import com.example.data.model.InvoiceEntity
import com.example.data.model.InvoiceItem
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.InputStream
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

object ZatcaXmlParser {

    fun parse(xmlString: String): ParsedInvoiceResult {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false // Simplifies tag queries without prefix matching
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xmlString)))
            doc.documentElement.normalize()
            extractFromDocument(doc, xmlString)
        } catch (e: Exception) {
            e.printStackTrace()
            ParsedInvoiceResult(
                isSuccess = false,
                errorMessage = "خطأ في تحليل ملف XML: ${e.localizedMessage}"
            )
        }
    }

    fun parse(inputStream: InputStream): ParsedInvoiceResult {
        val content = inputStream.bufferedReader().use { it.readText() }
        return parse(content)
    }

    private fun extractFromDocument(doc: Document, rawContent: String): ParsedInvoiceResult {
        val root = doc.documentElement

        // Invoice Number
        val invoiceNumber = getTextByTags(root, listOf("ID", "cbc:ID", "InvoiceNumber", "InvoiceId")) ?: ""

        // Date
        val invoiceDate = getTextByTags(root, listOf("IssueDate", "cbc:IssueDate", "Date", "InvoiceDate")) ?: ""

        // Supplier info
        val supplierName = getSupplierName(root)
        val supplierVat = getSupplierVat(root)

        // Customer info
        val customerName = getCustomerName(root)
        val customerVat = getCustomerVat(root)

        // Monetary totals
        val amountBeforeTax = getMonetaryValue(root, listOf("TaxExclusiveAmount", "cbc:TaxExclusiveAmount", "LineExtensionAmount", "cbc:LineExtensionAmount")) ?: 0.0
        val discount = getMonetaryValue(root, listOf("AllowanceTotalAmount", "cbc:AllowanceTotalAmount", "DiscountAmount")) ?: 0.0
        val vatAmount = getMonetaryValue(root, listOf("TaxAmount", "cbc:TaxAmount")) ?: 0.0
        var totalWithTax = getMonetaryValue(root, listOf("TaxInclusiveAmount", "cbc:TaxInclusiveAmount", "PayableAmount", "cbc:PayableAmount")) ?: 0.0

        if (totalWithTax == 0.0 && (amountBeforeTax > 0 || vatAmount > 0)) {
            totalWithTax = amountBeforeTax + vatAmount - discount
        }

        val vatRate = if (amountBeforeTax > 0 && vatAmount > 0) {
            val calcRate = (vatAmount / amountBeforeTax) * 100
            if (calcRate in 14.5..15.5) 15.0 else String.format("%.1f", calcRate).toDoubleOrNull() ?: 15.0
        } else {
            15.0
        }

        // Line Items
        val items = extractLineItems(root)

        return ParsedInvoiceResult(
            isSuccess = true,
            supplierName = supplierName,
            supplierVatNumber = supplierVat,
            customerName = customerName,
            customerVatNumber = customerVat,
            invoiceNumber = invoiceNumber,
            invoiceDate = invoiceDate,
            amountBeforeTax = amountBeforeTax,
            discountAmount = discount,
            vatRate = vatRate,
            vatAmount = vatAmount,
            totalWithTax = totalWithTax,
            items = items,
            rawContent = rawContent,
            sourceType = "XML"
        )
    }

    private fun getSupplierName(root: Element): String {
        val supplierParty = getFirstElement(root, listOf("AccountingSupplierParty", "cac:AccountingSupplierParty", "SupplierParty"))
        if (supplierParty != null) {
            val regName = getTextByTags(supplierParty, listOf("RegistrationName", "cbc:RegistrationName", "Name", "cbc:Name"))
            if (!regName.isNullOrBlank()) return regName
        }
        return getTextByTags(root, listOf("SupplierName", "SellerName")) ?: ""
    }

    private fun getSupplierVat(root: Element): String {
        val supplierParty = getFirstElement(root, listOf("AccountingSupplierParty", "cac:AccountingSupplierParty", "SupplierParty"))
        if (supplierParty != null) {
            val vat = getTextByTags(supplierParty, listOf("CompanyID", "cbc:CompanyID", "VATNumber", "TaxSchemeID"))
            if (!vat.isNullOrBlank() && vat.length >= 10) return vat
        }
        return getTextByTags(root, listOf("SupplierVAT", "SellerVAT", "VATRegistrationNumber")) ?: ""
    }

    private fun getCustomerName(root: Element): String {
        val customerParty = getFirstElement(root, listOf("AccountingCustomerParty", "cac:AccountingCustomerParty", "CustomerParty"))
        if (customerParty != null) {
            val regName = getTextByTags(customerParty, listOf("RegistrationName", "cbc:RegistrationName", "Name", "cbc:Name"))
            if (!regName.isNullOrBlank()) return regName
        }
        return getTextByTags(root, listOf("CustomerName", "BuyerName")) ?: ""
    }

    private fun getCustomerVat(root: Element): String {
        val customerParty = getFirstElement(root, listOf("AccountingCustomerParty", "cac:AccountingCustomerParty", "CustomerParty"))
        if (customerParty != null) {
            val vat = getTextByTags(customerParty, listOf("CompanyID", "cbc:CompanyID", "VATNumber"))
            if (!vat.isNullOrBlank()) return vat
        }
        return getTextByTags(root, listOf("CustomerVAT", "BuyerVAT")) ?: ""
    }

    private fun extractLineItems(root: Element): List<InvoiceItem> {
        val items = mutableListOf<InvoiceItem>()
        val lineNodes = getElementsByTags(root, listOf("InvoiceLine", "cac:InvoiceLine", "ItemLine"))
        for (i in 0 until lineNodes.size) {
            val lineElem = lineNodes[i]
            val name = getTextByTags(lineElem, listOf("Name", "cbc:Name", "Description", "cbc:Description")) ?: "صنف #${i + 1}"
            val qtyStr = getTextByTags(lineElem, listOf("InvoicedQuantity", "cbc:InvoicedQuantity", "Quantity"))
            val unitPriceStr = getTextByTags(lineElem, listOf("PriceAmount", "cbc:PriceAmount", "UnitPrice"))
            val totalStr = getTextByTags(lineElem, listOf("LineExtensionAmount", "cbc:LineExtensionAmount", "TotalAmount"))

            val qty = qtyStr?.toDoubleOrNull() ?: 1.0
            val price = unitPriceStr?.toDoubleOrNull() ?: 0.0
            val total = totalStr?.toDoubleOrNull() ?: (qty * price)

            items.add(InvoiceItem(name = name, quantity = qty, unitPrice = price, total = total))
        }
        return items
    }

    private fun getTextByTags(parent: Element, tagNames: List<String>): String? {
        for (tag in tagNames) {
            val nodes = parent.getElementsByTagName(tag)
            if (nodes.length > 0) {
                val text = nodes.item(0).textContent?.trim()
                if (!text.isNullOrBlank()) return text
            }
        }
        return null
    }

    private fun getFirstElement(parent: Element, tagNames: List<String>): Element? {
        for (tag in tagNames) {
            val nodes = parent.getElementsByTagName(tag)
            if (nodes.length > 0 && nodes.item(0) is Element) {
                return nodes.item(0) as Element
            }
        }
        return null
    }

    private fun getElementsByTags(parent: Element, tagNames: List<String>): List<Element> {
        val result = mutableListOf<Element>()
        for (tag in tagNames) {
            val nodes = parent.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node is Element) result.add(node)
            }
            if (result.isNotEmpty()) break
        }
        return result
    }

    private fun getMonetaryValue(parent: Element, tagNames: List<String>): Double? {
        val str = getTextByTags(parent, tagNames) ?: return null
        return str.replace(",", "").toDoubleOrNull()
    }
}
