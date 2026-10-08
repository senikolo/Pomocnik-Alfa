#!/usr/bin/env python3
"""Exercise the real Java countdown, including schedule/live distinction."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

root=Path(__file__).resolve().parent
with TemporaryDirectory() as tmp:
    pkg=Path(tmp)/"com/ispina/lokalnie/transit"
    pkg.mkdir(parents=True,exist_ok=True)
    (pkg/"DepartureCountdown.java").write_text((root/"DepartureCountdown.java").read_text(encoding="utf-8"),encoding="utf-8")
    test=Path(tmp)/"Test.java"
    test.write_text(r"""
import com.ispina.lokalnie.transit.DepartureCountdown;
public class Test {
    static void expect(long departure,long now,Integer delay,String expected){
        String actual=DepartureCountdown.label(departure,now,delay);
        if(!actual.equals(expected))
            throw new AssertionError("Expected '"+expected+"', got '"+actual+"'");
    }
    public static void main(String[] args){
        long now=1800000000000L;
        expect(now+180000,now,null,"Planowo za 3 min");
        expect(now+180000,now,3,"LIVE za 6 min");
        expect(now+180000,now,-1,"LIVE za 2 min");
        expect(now+15000,now,null,"Planowo za 1 min");
        expect(now,now,null,"Planowo · teraz");
        expect(now+180000,now+300000,null,"Po godzinie rozkładowej");
        expect(now-180000,now,3,"LIVE · teraz");
        expect(now+180000,now,121,"Planowo za 3 min");
        expect(0,now,null,"Nieznana godzina");
        System.out.println("PASS: 9 Java countdown cases including scheduled vs verified LIVE");
    }
}
""",encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8","-d",tmp,str(pkg/"DepartureCountdown.java"),str(test)],check=True)
    subprocess.run(["java","-cp",tmp,"Test"],check=True)
