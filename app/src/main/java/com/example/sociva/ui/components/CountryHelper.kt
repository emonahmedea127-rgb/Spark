package com.example.sociva.ui.components

import java.util.Locale

data class CountryInfo(
  val name: String,
  val code: String,
  val flag: String
)

object CountryHelper {

  fun getFlagEmoji(countryCode: String): String {
    val clean = countryCode.trim().uppercase()
    if (clean.length != 2) return ""
    return try {
      val firstChar = Character.codePointAt(clean, 0) - 0x41 + 0x1F1E6
      val secondChar = Character.codePointAt(clean, 1) - 0x41 + 0x1F1E6
      String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
    } catch (e: Exception) {
      ""
    }
  }

  val allCountries: List<CountryInfo> by lazy {
    val isoCodes = Locale.getISOCountries()
    isoCodes.mapNotNull { code ->
      val locale = Locale("", code)
      val name = locale.getDisplayCountry(Locale.ENGLISH)
      if (name.isNotBlank()) {
        CountryInfo(
          name = name,
          code = code.uppercase(),
          flag = getFlagEmoji(code)
        )
      } else {
        null
      }
    }.distinctBy { it.code }
      .sortedBy { it.name }
  }

  fun findCountryByName(name: String): CountryInfo? {
    if (name.isBlank()) return null
    return allCountries.find { it.name.equals(name.trim(), ignoreCase = true) }
      ?: allCountries.find { it.name.contains(name.trim(), ignoreCase = true) }
  }

  fun findCountryByCode(code: String): CountryInfo? {
    if (code.isBlank()) return null
    return allCountries.find { it.code.equals(code.trim(), ignoreCase = true) }
  }

  fun getFlagForCountry(countryNameOrCode: String): String {
    if (countryNameOrCode.isBlank()) return ""
    val byCode = findCountryByCode(countryNameOrCode)
    if (byCode != null) return byCode.flag
    val byName = findCountryByName(countryNameOrCode)
    if (byName != null) return byName.flag
    return getFlagEmoji(countryNameOrCode.take(2))
  }
}
