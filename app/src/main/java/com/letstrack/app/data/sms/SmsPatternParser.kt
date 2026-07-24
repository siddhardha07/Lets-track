package com.letstrack.app.data.sms

import android.util.Log

/**
 * Parses sample SMS messages to extract patterns and learn bank SMS format
 */
class SmsPatternParser {
    
    data class ParsedSmsPattern(
        val accountNumber: String,
        val bankName: String,
        val senderHints: List<String>,
        val debitKeywords: List<String>,
        val creditKeywords: List<String>,
        val amountPattern: String,
        val datePattern: String,
        val balancePattern: String,
        val accountPattern: String,
        val merchantPattern: String = ""
    )
    
    fun parseAccountFromSamples(debitSms: String, creditSms: String): ParsedSmsPattern? {
        try {
            Log.d("SmsPatternParser", "Parsing debit SMS: ${debitSms.take(100)}...")
            Log.d("SmsPatternParser", "Parsing credit SMS: ${creditSms.take(100)}...")
            
            // Extract account number
            val accountNumber = extractAccountNumber(debitSms, creditSms)
            val finalAccountNumber = accountNumber.ifEmpty { "0000" }
            Log.d("SmsPatternParser", "Extracted account number: $finalAccountNumber")
            
            // Extract bank name
            val bankName = extractBankName(debitSms, creditSms)
            Log.d("SmsPatternParser", "Extracted bank name: $bankName")
            
            // Extract sender hints
            val senderHints = extractSenderHints(debitSms, creditSms)
            Log.d("SmsPatternParser", "Extracted sender hints: $senderHints")
            
            // Extract keywords
            val debitKeywords = extractDebitKeywords(debitSms)
            val creditKeywords = extractCreditKeywords(creditSms)
            
            return ParsedSmsPattern(
                accountNumber = finalAccountNumber,
                bankName = bankName,
                senderHints = senderHints,
                debitKeywords = debitKeywords,
                creditKeywords = creditKeywords,
                amountPattern = generateAmountPattern(),
                datePattern = generateDatePattern(),
                balancePattern = generateBalancePattern(),
                accountPattern = generateAccountPattern(),
                merchantPattern = generateMerchantPattern()
            )
        } catch (e: Exception) {
            Log.e("SmsPatternParser", "Error parsing SMS patterns: ${e.message}", e)
            return null
        }
    }
    
    private fun extractAccountNumber(debitSms: String, creditSms: String): String {
        val patterns = listOf(
            "A/[Cc]\\s*[Xx]*(\\d{4,10})".toRegex(),
            "account\\s*(?:no\\.?|number)?\\s*[Xx]*(\\d{4,10})".toRegex(RegexOption.IGNORE_CASE),
            "ending\\s*(\\d{4,10})".toRegex(RegexOption.IGNORE_CASE),
            "end\\s*(\\d{4,10})".toRegex(RegexOption.IGNORE_CASE),
            "card\\s*(?:no\\.?)?\\s*[Xx]*(\\d{4,10})".toRegex(RegexOption.IGNORE_CASE),
            "A/?[Cc]\\s+(\\d{4,10})".toRegex()
        )
        
        val debitNumbers = mutableSetOf<String>()
        val creditNumbers = mutableSetOf<String>()
        
        patterns.forEach { pattern ->
            pattern.findAll(debitSms).forEach { debitNumbers.add(it.groupValues[1]) }
            pattern.findAll(creditSms).forEach { creditNumbers.add(it.groupValues[1]) }
        }
        
        Log.d("SmsPatternParser", "Debit numbers: $debitNumbers")
        Log.d("SmsPatternParser", "Credit numbers: $creditNumbers")
        
        // Prefer numbers that appear in both
        val common = debitNumbers.intersect(creditNumbers)
        if (common.isNotEmpty()) {
            return common.maxByOrNull { it.length } ?: common.first()
        }
        
        // Fallback: Use any 4+ digit number found
        val allNumbers = debitNumbers + creditNumbers
        return allNumbers.filter { it.length >= 4 }.maxByOrNull { it.length } ?: ""
    }
    
    private fun extractBankName(debitSms: String, creditSms: String): String {
        val patterns = listOf(
            "Team\\s+([A-Z][A-Za-z\\s&]+(?:Bank|BANK))".toRegex(),
            "([A-Z][A-Za-z\\s&]+Bank)".toRegex(RegexOption.IGNORE_CASE),
            "(HDFC|ICICI|SBI|Axis|Kotak|IDFC|Yes Bank|IndusInd|PNB|Bank of Baroda|Canara Bank)".toRegex(RegexOption.IGNORE_CASE)
        )
        
        val combinedSms = "$debitSms $creditSms"
        
        patterns.forEach { pattern ->
            pattern.find(combinedSms)?.groupValues?.get(1)?.let { bankName ->
                val cleaned = bankName.trim()
                if (cleaned.length > 2) {
                    return cleaned
                }
            }
        }
        
        return "My Bank"
    }
    
    private fun extractSenderHints(debitSms: String, creditSms: String): List<String> {
        val hints = mutableSetOf<String>()
        
        val bankName = extractBankName(debitSms, creditSms)
        val words = bankName.replace(Regex("[^A-Za-z ]"), "").split(" ").filter { it.isNotEmpty() }
        
        if (words.isNotEmpty()) {
            hints.add(words[0].uppercase())
            
            if (words.size >= 2) {
                hints.add(words.joinToString("") { it.take(1) }.uppercase())
                hints.add(words.take(2).joinToString("").uppercase())
            }
        }
        
        if (hints.isEmpty()) {
            hints.addAll(listOf("BANK", "INFO"))
        }
        
        return hints.toList()
    }
    
    private fun extractDebitKeywords(debitSms: String): List<String> {
        val keywords = mutableSetOf<String>()
        val lowerSms = debitSms.lowercase()
        
        listOf("debited", "debit", "withdrawn", "spent", "paid").forEach { word ->
            if (lowerSms.contains(word)) keywords.add(word)
        }
        
        return keywords.toList().ifEmpty { listOf("debited") }
    }
    
    private fun extractCreditKeywords(creditSms: String): List<String> {
        val keywords = mutableSetOf<String>()
        val lowerSms = creditSms.lowercase()
        
        listOf("credited", "credit", "deposited", "received").forEach { word ->
            if (lowerSms.contains(word)) keywords.add(word)
        }
        
        return keywords.toList().ifEmpty { listOf("credited") }
    }
    
    private fun generateAmountPattern(): String {
        return "(?:Rs\\.?|INR)\\s*([\\d,]+(?:\\.\\d{2})?)"
    }
    
    private fun generateDatePattern(): String {
        return "(\\d{2}[/-]\\d{2}[/-]\\d{2,4})(?:\\s+(\\d{2}:\\d{2}))?"
    }
    
    private fun generateBalancePattern(): String {
        return "(?i)(?:available\\s+balance|new\\s+balance|balance|bal).*?(?:Rs\\.?|INR)?\\s*([\\d,]+\\.\\d{2})"
    }
    
    private fun generateAccountPattern(): String {
        return "A/[Cc]\\s*[Xx]*(\\d{4,10})"
    }
    
    private fun generateMerchantPattern(): String {
        return "(?:to|at):\\s*([A-Za-z][A-Za-z\\s]+?)(?:credited|debited|\\.|;|$)"
    }
}
