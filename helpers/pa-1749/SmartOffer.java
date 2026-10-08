package com.ispina.lokalnie.deals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative, comparable product recommendation based only on parsed source facts. */
public final class SmartOffer {
    private SmartOffer(){}
    public static final class Pick {
        public final Deal best;
        public final Deal easier;
        public final int comparable, unknown;
        public final String reasoning;
        Pick(Deal best,Deal easier,int comparable,int unknown,String reasoning){
            this.best=best;this.easier=easier;this.comparable=comparable;this.unknown=unknown;this.reasoning=reasoning;
        }
    }
    private static boolean valid(Deal d){
        return d!=null && d.price>0 && Double.isFinite(d.price) &&
            d.unitPrice>0 && Double.isFinite(d.unitPrice) &&
            ("kg".equals(d.unit)||"l".equals(d.unit)||"szt.".equals(d.unit)) &&
            d.minimum>=1 && d.minimum<=100 && d.details &&
            d.to>=System.currentTimeMillis() && d.url!=null && d.url.startsWith("https://");
    }
    public static String unitName(String unit){
        if("kg".equals(unit))return "kg";
        if("l".equals(unit))return "l";
        if("szt.".equals(unit))return "szt.";
        return unit;
    }
    public static double checkout(Deal d){return d.price*Math.max(1,d.minimum);}
    /**
     * Unit price is primary. A slight penalty prefers offers not requiring loyalty cards
     * or buying unnecessary packs, when their prices are close.
     */
    public static double score(Deal d){
        return d.unitPrice * (1.0+(d.card?.035:0)+.025*Math.min(6,Math.max(0,d.minimum-1)));
    }
    public static Pick choose(List<Deal> offers,String phrase){
        List<Deal> comparable=new ArrayList<>();
        int unknown=0;
        for(Deal d:offers){
            if(valid(d))comparable.add(d);
            else unknown++;
        }
        if(comparable.isEmpty())
            return new Pick(null,null,0,unknown,
                "Za mało potwierdzonych danych o wadze, liczbie sztuk lub warunkach. Nie wskazuję zwycięzcy na ślepo.");
        String preferred=DealsRepository.preferredUnit(phrase);
        String selected=preferred;
        if(selected.isEmpty() || !containsUnit(comparable,selected)){
            int countKg=0,countL=0,countPc=0;
            for(Deal d:comparable){if("kg".equals(d.unit))countKg++;else if("l".equals(d.unit))countL++;else countPc++;}
            selected=countKg>=countL&&countKg>=countPc?"kg":countL>=countPc?"l":"szt.";
        }
        List<Deal> same=new ArrayList<>();
        for(Deal d:comparable)if(selected.equals(d.unit))same.add(d);
        Collections.sort(same,Comparator.comparingDouble(SmartOffer::score).thenComparingDouble(d->d.unitPrice));
        Deal best=same.get(0), easier=null;
        for(Deal d:same)if(d.minimum==1&&!d.card && d!=best && (easier==null || d.unitPrice<easier.unitPrice))easier=d;
        String reason="Smart oferta: "+best.store+" · "+best.money(best.unitPrice)+" / "+unitName(best.unit)+
            ". Do zapłaty co najmniej "+best.money(checkout(best))+" ("+
            Math.max(1,best.minimum)+" "+(best.minimum==1?"szt.":"szt.")+")"+
            (best.card?" · wymagana karta lub aplikacja.":". Bez potwierdzonego wymogu karty.")+
            " Porównano "+same.size()+" ofert/y o zgodnej jednostce."+
            (unknown>0?" Pominięto "+unknown+" ofert z brakami danych.":"");
        return new Pick(best,easier,same.size(),unknown,reason);
    }
    private static boolean containsUnit(List<Deal> ds,String unit){
        for(Deal d:ds)if(unit.equals(d.unit))return true;
        return false;
    }
    public static void enrichCount(Deal d){
        if(d==null||!d.unit.isEmpty()||d.price<=0||!Double.isFinite(d.price))return;
        String text=d.name+" "+d.description;
        // Avoid treating promotion "buy 2" as the pack's piece count.
        Matcher m=Pattern.compile("(?i)(?:op(?:akowanie|ak\\.?)?\\s*(?:po|:)?\\s*|zestaw\\s*)(\\d{1,3})\\s*(?:szt\\.?|sztuk|kapsułek|tabletek|rolek)\\b|(\\d{1,3})\\s*(?:szt\\.?|sztuk|kapsułek|tabletek|rolek)\\s*(?:w\\s*opakowaniu|opak\\.?)").matcher(text);
        if(m.find()){
            int pieces=Integer.parseInt(m.group(1)!=null?m.group(1):m.group(2));
            if(pieces>=1&&pieces<=500){d.unit="szt.";d.unitPrice=d.price/pieces;}
        }
    }
}
