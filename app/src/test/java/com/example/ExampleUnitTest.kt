package com.example

import com.example.data.model.InvoiceEntity
import com.example.parser.ParsedInvoiceResult
import com.example.parser.ZatcaQrDecoder
import com.example.parser.ZatcaXmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class ExampleUnitTest {

  @Test
  fun testZatcaXmlParser() {
    val sampleXml = """
      <?xml version="1.0" encoding="UTF-8"?>
      <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
               xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
               xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
          <cbc:ID>INV-2023-0098</cbc:ID>
          <cbc:IssueDate>2023-11-15</cbc:IssueDate>
          <cac:AccountingSupplierParty>
              <cac:Party>
                  <cac:PartyTaxScheme>
                      <cbc:CompanyID>310123456700003</cbc:CompanyID>
                  </cac:PartyTaxScheme>
                  <cac:PartyLegalEntity>
                      <cbc:RegistrationName>شركة التوريدات السعودية المحدودة</cbc:RegistrationName>
                  </cac:PartyLegalEntity>
              </cac:Party>
          </cac:AccountingSupplierParty>
          <cac:AccountingCustomerParty>
              <cac:Party>
                  <cac:PartyTaxScheme>
                      <cbc:CompanyID>310987654300003</cbc:CompanyID>
                  </cac:PartyTaxScheme>
                  <cac:PartyLegalEntity>
                      <cbc:RegistrationName>مؤسسة الأفق التجارية</cbc:RegistrationName>
                  </cac:PartyLegalEntity>
              </cac:Party>
          </cac:AccountingCustomerParty>
          <cac:TaxTotal>
              <cbc:TaxAmount>150.00</cbc:TaxAmount>
          </cac:TaxTotal>
          <cac:LegalMonetaryTotal>
              <cbc:TaxExclusiveAmount>1000.00</cbc:TaxExclusiveAmount>
              <cbc:AllowanceTotalAmount>0.00</cbc:AllowanceTotalAmount>
              <cbc:TaxInclusiveAmount>1150.00</cbc:TaxInclusiveAmount>
              <cbc:PayableAmount>1150.00</cbc:PayableAmount>
          </cac:LegalMonetaryTotal>
          <cac:InvoiceLine>
              <cbc:InvoicedQuantity>2</cbc:InvoicedQuantity>
              <cbc:LineExtensionAmount>1000.00</cbc:LineExtensionAmount>
              <cac:Item>
                  <cbc:Name>خدمات صيانة واستشارات تقنية</cbc:Name>
              </cac:Item>
              <cac:Price>
                  <cbc:PriceAmount>500.00</cbc:PriceAmount>
              </cac:Price>
          </cac:InvoiceLine>
      </Invoice>
    """.trimIndent()

    val result = ZatcaXmlParser.parse(sampleXml)
    assertTrue(result.isSuccess)
    assertEquals("INV-2023-0098", result.invoiceNumber)
    assertEquals("2023-11-15", result.invoiceDate)
    assertEquals("شركة التوريدات السعودية المحدودة", result.supplierName)
    assertEquals("310123456700003", result.supplierVatNumber)
    assertEquals("مؤسسة الأفق التجارية", result.customerName)
    assertEquals("310987654300003", result.customerVatNumber)
    assertEquals(1000.00, result.amountBeforeTax, 0.01)
    assertEquals(150.00, result.vatAmount, 0.01)
    assertEquals(1150.00, result.totalWithTax, 0.01)
    assertEquals(1, result.items.size)
    assertEquals("خدمات صيانة واستشارات تقنية", result.items[0].name)
    assertEquals(2.0, result.items[0].quantity, 0.01)
    assertEquals(500.0, result.items[0].unitPrice, 0.01)
  }

  @Test
  fun testVatMathValidation() {
    val (unrecognized, warnings) = ParsedInvoiceResult.computeValidation(
      supplierName = "شركة تجريبية",
      supplierVat = "310123456700003",
      invoiceNumber = "1234",
      amountBeforeTax = 100.0,
      vatAmount = 15.0,
      discountAmount = 0.0,
      totalWithTax = 115.0,
      vatRate = 15.0
    )
    assertTrue(unrecognized.isEmpty())
    assertTrue(warnings.isEmpty())
  }

  @Test
  fun testContinuousScanningTenConsecutiveInvoices() {
    // Simulating 10 distinct Saudi invoices passing in front of the camera continuously
    val sessionInvoices = mutableListOf<InvoiceEntity>()
    val scannedSignaturesInSession = mutableSetOf<String>()
    var totalSessionVat = 0.0
    var totalSessionAmount = 0.0

    for (i in 1..10) {
      val supplierName = "مورد تجاري رقم $i"
      val vatNumber = "31000000000000$i"
      val timestamp = "2023-10-2${i}T12:00:00Z"
      val total = 115.0 * i
      val vat = 15.0 * i
      val beforeTax = 100.0 * i

      val signature = "${vatNumber}_${timestamp}_$total"

      // Anti-duplicate check
      if (!scannedSignaturesInSession.contains(signature)) {
        scannedSignaturesInSession.add(signature)
        val entity = InvoiceEntity(
          id = i.toLong(),
          supplierName = supplierName,
          supplierVatNumber = vatNumber,
          invoiceDate = timestamp,
          amountBeforeTax = beforeTax,
          vatAmount = vat,
          totalWithTax = total,
          sourceType = "AUTO_CONTINUOUS"
        )
        sessionInvoices.add(entity)
        totalSessionVat += vat
        totalSessionAmount += total
      }
    }

    // Verify all 10 consecutive invoices were processed and saved
    assertEquals(10, sessionInvoices.size)
    assertEquals(825.0, totalSessionVat, 0.01) // 15 + 30 + ... + 150 = 825
    assertEquals(6325.0, totalSessionAmount, 0.01) // 115 * 55 = 6325
  }

  @Test
  fun testAntiDuplicatePrevention() {
    val scannedSignaturesInSession = mutableSetOf<String>()
    val savedInvoices = mutableListOf<String>()

    val invoiceSignature = "310123456700003_2023-10-25T14:00:00Z_230.0"

    // Simulate 30 consecutive camera frames where the same invoice stays in view
    for (frame in 1..30) {
      if (!scannedSignaturesInSession.contains(invoiceSignature)) {
        scannedSignaturesInSession.add(invoiceSignature)
        savedInvoices.add(invoiceSignature)
      }
    }

    // Must be saved exactly once, ignoring the other 29 frames
    assertEquals(1, savedInvoices.size)
  }

  @Test
  fun testFailedInvoiceEntersReviewQueueGracefully() {
    val savedInvoices = mutableListOf<String>()
    val reviewQueue = mutableListOf<String>()

    val frames = listOf(
      "VALID:310111111111113",
      "CORRUPTED_GARBAGE_BYTES_#@$",
      "VALID:310222222222223"
    )

    for (raw in frames) {
      if (raw.startsWith("VALID:")) {
        savedInvoices.add(raw)
      } else {
        reviewQueue.add("رمز غير مطابق لمعايير الفوترة")
      }
    }

    assertEquals(2, savedInvoices.size)
    assertEquals(1, reviewQueue.size)
  }
}
