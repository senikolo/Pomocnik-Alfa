package com.ispina.lokalnie.transit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;

/** Pure-Java fixture tests for real GTFS-RT fields and stale/ambiguous data guards. */
public final class WarsawVehicleGpsTest {
    private static void var(ByteArrayOutputStream b,long v){
        while((v & ~127L)!=0){b.write((int)(v&127L)|128);v>>>=7;}
        b.write((int)v);
    }
    private static void fieldVar(ByteArrayOutputStream b,int field,long v){
        var(b,(field<<3));var(b,v);
    }
    private static void fieldBytes(ByteArrayOutputStream b,int field,byte[] value){
        var(b,(field<<3)|2);var(b,value.length);b.write(value,0,value.length);
    }
    private static byte[] bytes(ByteArrayOutputStream b){return b.toByteArray();}
    private static byte[] text(String value){return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    private static void floatField(ByteArrayOutputStream b,int field,float x){
        var(b,(field<<3)|5);int raw=Float.floatToIntBits(x);
        for(int i=0;i<4;i++)b.write((raw>>>(8*i))&255);
    }
    private static byte[] feed(long header,long posTime,String route){
        ByteArrayOutputStream h=new ByteArrayOutputStream();
        fieldBytes(h,1,text("2.0"));fieldVar(h,3,header);
        ByteArrayOutputStream trip=new ByteArrayOutputStream();
        fieldBytes(trip,5,text(route));
        ByteArrayOutputStream coord=new ByteArrayOutputStream();
        floatField(coord,1,52.2300f);floatField(coord,2,21.0100f);
        ByteArrayOutputStream vehicle=new ByteArrayOutputStream();
        fieldBytes(vehicle,1,bytes(trip));fieldBytes(vehicle,2,bytes(coord));
        fieldVar(vehicle,5,posTime);
        ByteArrayOutputStream entity=new ByteArrayOutputStream();
        fieldBytes(entity,1,text("v1"));fieldBytes(entity,4,bytes(vehicle));
        ByteArrayOutputStream whole=new ByteArrayOutputStream();
        fieldBytes(whole,1,bytes(h));fieldBytes(whole,2,bytes(entity));
        return bytes(whole);
    }
    private static void check(boolean value,String label){
        if(!value)throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        final long now=1781000000000L,secs=now/1000;
        byte[] current=feed(secs-10,secs-8,"517");
        WarsawVehicleGps.Snapshot right=WarsawVehicleGps.parse(
            current,Collections.singleton("517"),52.2300,21.0100,now);
        check(right.vehicles==1,"current matched route");
        check(right.nearestMeters<10,"nearby distance");
        check(right.feedTimeMillis==(secs-10)*1000,"header timestamp");
        WarsawVehicleGps.Snapshot wrong=WarsawVehicleGps.parse(
            current,Collections.singleton("190"),52.2300,21.0100,now);
        check(wrong.vehicles==0,"wrong route must not match");
        WarsawVehicleGps.Snapshot oldVehicle=WarsawVehicleGps.parse(
            feed(secs-10,secs-500,"517"),Collections.singleton("517"),
            Double.NaN,Double.NaN,now);
        check(oldVehicle.vehicles==0,"stale vehicle rejected");
        boolean rejected=false;
        try{
            WarsawVehicleGps.parse(feed(secs-500,secs-8,"517"),
                Collections.singleton("517"),52.23,21.01,now);
        }catch(IOException expected){rejected=true;}
        check(rejected,"stale feed rejected");
        rejected=false;
        try{
            WarsawVehicleGps.parse(new byte[]{(byte)0xff,(byte)0xff},
                Collections.singleton("517"),52.23,21.01,now);
        }catch(IOException expected){rejected=true;}
        check(rejected,"invalid protobuf rejected");
        System.out.println("PASS: GPS protobuf: route matching, coordinates, 120s freshness, malformed data");
    }
}
