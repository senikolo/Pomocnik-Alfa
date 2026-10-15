package com.ispina.lokalnie;

import java.text.Normalizer;
import java.util.Locale;

/** Clear, function-matched dashboard glyphs. The original Alfa cat remains the launcher icon. */
public final class PAIcons {
 private PAIcons() {}

 private static String normalized(String label) {
  if (label == null) return "";
  return Normalizer.normalize(label, Normalizer.Form.NFD)
      .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
      .replace('ł', 'l').trim();
 }
 private static boolean has(String label, String... names) {
  for (String name : names) if (label.contains(name)) return true;
  return false;
 }
 public static String glyph(String label) {
  String s = normalized(label);
  if (s.isEmpty()) return "📱";
  if (has(s, "alfa kids", "dla dzieci", "dziecko", "bajki")) return "🧸";
  if (has(s, "alfa tube", "tube", "wideo", "filmy")) return "▶";
  if (has(s, "tramwaj")) return "🚋";
  if (has(s, "pociag", "kolej", "skm", "wkd", "pkp")) return "🚆";
  if (has(s, "przystank", "odjazd", "autobus", "pojazd", "transport")) return "🚌";
  if (has(s, "planer", "trasa", "podroz", "nawigacj")) return "🧭";
  if (has(s, "mapa", "korki", "lokalizacj", "gps")) return "🗺";
  if (has(s, "asystent", "pomocnik", "chat", "czat")) return "💬";
  if (has(s, "telewizj", "tv")) return "📺";
  if (has(s, "radio", "stacje fm")) return "📻";
  if (has(s, "pogod", "prognoz", "temperatur")) return "🌤";
  if (has(s, "lotnictw", "lotnisk", "samolot", "loty")) return "✈";
  if (has(s, "spokoj", "relaks", "wyciszen", "medytacj")) return "🌿";
  if (has(s, "promocj", "okazj", "zakup", "gazetk", "lowca")) return "🛒";
  if (has(s, "przypomn", "powiadom", "alarm", "budzik")) return "🔔";
  if (has(s, "subskrypcj", "kalendarz", "terminarz")) return "🗓";
  if (has(s, "smart saver", "bateria", "energia")) return "🔋";
  if (has(s, "oszczedzacz", "oszczedno", "wydatk", "finans")) return "💰";
  if (has(s, "czytnik", "ksiaz", "lektur")) return "📖";
  if (has(s, "notat", "zapiski")) return "📝";
  if (has(s, "dzwiek", "muzyk", "odglos")) return "🎵";
  if (has(s, "wschod", "zachod slonca")) return "🌅";
  if (has(s, "senior", "dostepnos")) return "👓";
  if (has(s, "ulubion", "zakladk")) return "⭐";
  if (has(s, "ustawien", "motyw", "wyglad")) return "⚙";
  if (has(s, "informacj", "o aplikacji")) return "ℹ";
  return "📱";
 }
}
