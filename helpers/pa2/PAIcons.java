package com.ispina.lokalnie;
/** Semantic dashboard glyphs; the original Alfa cat remains the launcher icon. */
public final class PAIcons {
 public static String glyph(String label) {
  if(label==null)return "•";
  if(label.startsWith("Planer"))return "🧭";
  if(label.contains("Kids"))return "🧸";
  if(label.contains("Tube"))return "▶";
  if(label.startsWith("Odjazdy"))return "🚌";
  if(label.contains("Pogoda"))return "🌤";
  if(label.equals("Radio"))return "📻";
  if(label.equals("Telewizja"))return "📺";
  if(label.contains("Lotnictwo")||label.contains("lotnisk"))return "✈";
  if(label.contains("spokoju"))return "🌿";
  if(label.contains("promocji"))return "🛒";
  if(label.contains("Przypomnienia"))return "🔔";
  if(label.contains("Subskrypcje"))return "🗓";
  if(label.contains("Smart Saver"))return "💡";
  if(label.contains("Oszczędzacz"))return "💰";
  if(label.contains("Czytnik"))return "📖";
  if(label.contains("Notat"))return "📝";
  if(label.contains("Ustawienia"))return "⚙";
  return "•";
 }
}
