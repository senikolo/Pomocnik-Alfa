#!/usr/bin/env python3
"""Compile and exercise the actual SmartOffer.java using small dependency stubs."""
from pathlib import Path
import subprocess
import tempfile

source = Path(__file__).with_name("SmartOffer.java")
package = "com/ispina/lokalnie/deals"

stub_deal = r'''package com.ispina.lokalnie.deals;
public class Deal {
  public String name="",description="",unit="",store="",url="";
  public double price,unitPrice;
  public int minimum=1;
  public boolean details,card;
  public long to=Long.MAX_VALUE;
  public String money(double v){ return String.format(java.util.Locale.ROOT,"%.2f zł",v); }
}'''

stub_repository = r'''package com.ispina.lokalnie.deals;
public class DealsRepository {
  public static String preferredUnit(String query){ return query.equals("mleko")?"l":""; }
}'''

checks = r'''package com.ispina.lokalnie.deals;
import java.util.*;
public class SmartOfferRegression {
  private static void check(boolean value, String reason) {
    if(!value)throw new AssertionError(reason);
  }
  private static Deal deal(double price,int min,boolean card){
    Deal d = new Deal(); d.name="mleko";d.store="Sklep";d.price=price;d.unitPrice=price;
    d.unit="l";d.minimum=min;d.card=card;d.details=true;d.url="https://example.com/";
    return d;
  }
  public static void main(String[] args){
    for(String name:new String[]{"opakowanie 24 szt.","zestaw 12 rolek", "30 kapsułek w opakowaniu"}){
      Deal d = new Deal();d.price=48;d.name=name;
      SmartOffer.enrichCount(d);
      check(d.unit.equals("szt."), "Missing count for: "+name);
      check(d.unitPrice>0&&d.unitPrice<=4,"Bad price per piece: "+name);
    }
    Deal campaign=new Deal();campaign.price=8;campaign.name="Kup 2 produkty";
    SmartOffer.enrichCount(campaign);
    check(campaign.unit.isEmpty(),"Promotional purchase count is not pack size");
    Deal regular=deal(4.20,1,false), bundle=deal(3.50,2,true);
    SmartOffer.Pick pick=SmartOffer.choose(Arrays.asList(regular,bundle),"mleko");
    check(pick.best==bundle,"Real savings win even with card/quantity penalty");
    check(Math.abs(SmartOffer.checkout(bundle)-7.0)<0.00001,"Minimum checkout includes 2 units");
    Deal almost=deal(4.00,2,true);
    check(SmartOffer.choose(Arrays.asList(regular,almost),"mleko").best==regular,
       "Small savings should not override card and minimum purchase inconvenience");
    Deal unknown=deal(4.0,1,false);unknown.details=false;
    check(SmartOffer.choose(Collections.singletonList(unknown),"mleko").best==null,
       "Do not recommend offers with unverified terms");
    System.out.println("PASS: SmartOffer pack count, campaign, checkout, ranking, terms");
  }
}'''

with tempfile.TemporaryDirectory(prefix="pa-smart-test-") as directory:
    folder = Path(directory) / package
    folder.mkdir(parents=True)
    (folder / "SmartOffer.java").write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
    (folder / "Deal.java").write_text(stub_deal, encoding="utf-8")
    (folder / "DealsRepository.java").write_text(stub_repository, encoding="utf-8")
    (folder / "SmartOfferRegression.java").write_text(checks, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", *map(str,folder.glob("*.java"))], check=True)
    subprocess.run(["java", "-cp", directory, "com.ispina.lokalnie.deals.SmartOfferRegression"], check=True)
