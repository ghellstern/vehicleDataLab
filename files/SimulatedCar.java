//Every second, send a message - but actually move forward 15 minutes each time
//Choose a random car
//Add a random distance - last time - randomise 0 (25%) or 30-90mph
//Choose a random fuel consumption and reduce the fuel, or fill it up - 
//	Calculate distance since last sample
//	At 90mph, reduce fuel by 20mpg
//	60, 30mpg
//	30, 40mpg
//	Fuel reduction is economy = distance/consumption (mpg = miles/gallons)
//	economy = 40 - (speed-30)/(random(2.5,4.5)

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.lang.StringBuilder;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class SimulatedCar
{
    private String vehicleNumber;
    private String authToken;
    private double fuelRemaining;
    private double fuelCapacity;
    private double distance;
    private LocalDateTime ts;

    static Random generator = new Random(System.currentTimeMillis());
    static String orgId = null;

    public SimulatedCar(String vehicleNumber,
                        String authToken,
                        double fuelRemaining,
                        double fuelCapacity,
                        double distance,
                        LocalDateTime ts)
    {
        this.vehicleNumber = vehicleNumber;
        this.authToken = authToken;
        this.fuelRemaining = fuelRemaining;
        this.fuelCapacity = fuelCapacity;
        this.distance = distance;
        this.ts = ts;
    }

    public void pulse(LocalDateTime pulseTs)
    {
        long minsElapsed = ChronoUnit.MINUTES.between(this.ts, pulseTs);
        int refills = 0;

        if (minsElapsed > 0)
        {
            double distanceRandom = generator.nextDouble();

            // [0, 0.25] => car doesn't move during this interval
            if (distanceRandom > 0.25)
            {
                double speed = 30.0 + 60.0 * distanceRandom;
                double dist = speed * minsElapsed / 60.0;
                double fuelEconomy = 40.0 - ((speed - 30.0) / (generator.nextDouble() * 2.0 + 2.5));
                double fuelConsumed = dist / fuelEconomy;

                if (fuelConsumed >= this.fuelRemaining)
                {
                    this.fuelRemaining -= fuelConsumed;
                    refills = 1 - (int)(this.fuelRemaining / this.fuelCapacity);
                    this.fuelRemaining += this.fuelCapacity * refills;
                }
                else
                {
                    this.fuelRemaining -= fuelConsumed;
                }
                this.distance += dist;
            }
        }
        else
        {
            // Do nothing
        }

        this.ts = pulseTs;

        publishEvent(refills);
    }

    private void publishEvent(int refills)
    {
        String clientId = "d:" + SimulatedCar.orgId + ":car:" + this.vehicleNumber;
        String broker = "ssl://" + SimulatedCar.orgId + ".messaging.internetofthings.ibmcloud.com:8883";
        String topic = "iot-2/evt/update/fmt/json";
        MemoryPersistence persistence = new MemoryPersistence();

        try
        {
            MqttClient client = new MqttClient(broker, clientId, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setUserName("use-token-auth");
            connOpts.setPassword(this.authToken.toCharArray());
            client.connect(connOpts);
            String eventPayload = toJsonForEvent(refills);
            System.out.println(eventPayload);

            MqttMessage message = new MqttMessage(eventPayload.getBytes());
            message.setQos(0);
            client.publish(topic, message);
            client.disconnect();
        }
        catch (MqttException me)
        {
            System.out.println("reason " + me.getReasonCode());
            System.out.println("msg " + me.getMessage());
            System.out.println("loc " + me.getLocalizedMessage());
            System.out.println("cause " + me.getCause());
            System.out.println("excep " + me);
            me.printStackTrace();
            System.exit(1);
        }
    }

    private String toJsonForEvent(int refills)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("  \"vehicleNumber\" : \"" + this.vehicleNumber + "\",");
        sb.append("  \"fuelRemaining\" : " + this.fuelRemaining + ",");
        sb.append("  \"fuelCapacity\" : " + this.fuelCapacity + ",");
        sb.append("  \"distance\" : " + this.distance + ",");
        sb.append("  \"refills\" : " + refills + ",");
        sb.append("  \"ts\" : \"" + this.ts + "\"");
        sb.append("}");
        return sb.toString();
    }

    public String toJson()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"vehicleNumber\" : \"" + this.vehicleNumber + "\",\n");
        sb.append("  \"fuelRemaining\" : " + this.fuelRemaining + ",\n");
        sb.append("  \"fuelCapacity\" : " + this.fuelCapacity + ",\n");
        sb.append("  \"distance\" : " + this.distance + ",\n");
        sb.append("  \"ts\" : \"" + this.ts + "\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    public static void main(String args[])
    {
        orgId = System.getenv("IOT_ORG_ID");
        if (orgId == null)
        {
            System.err.println("You must set environment variable IOT_ORG_ID");
            System.exit(1);
        }

        LocalDateTime ts = LocalDateTime.now();

        SimulatedCar cars[] = new SimulatedCar[20];
        for (int i = 0; i < 20; i++)
        {
            cars[i] = new SimulatedCar("car" + (i + 1), String.format("token%1$03d", i + 1), 9, 11, 0, ts);
        }

        while (true)
        {
            int gen = generator.nextInt(20);
            ts = ts.plusMinutes(15);
            cars[gen % 20].pulse(ts);
            try
            {
                Thread.sleep(1000);    
            }
            catch (InterruptedException e)
            {
                //TODO: handle exception
            }
        }
    }
}