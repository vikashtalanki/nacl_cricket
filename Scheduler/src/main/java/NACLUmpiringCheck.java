import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class NACLUmpiringCheck {
    static Set<String> divAGroupA = new HashSet<>(Arrays.asList("2point0","Dark Knights","Dragons","Fire Friends","Grizzlies","Mighty Warriors","Knight Walkers (CA)","Royal Kings","Kings X1","Lords","Seamers"));
    static Set<String> divAGroupB = new HashSet<>(Arrays.asList("Astros+","Bleed Blue","Money Heist","Ghadeer CC","Thunderbolts","Royal Knights","P@nthers XI","CSK Yorkers","Singh Cricket Club","JerseyXI","Smoking Sadhoos"));
    static Set<String> divBGroupA = new HashSet<>(Arrays.asList("Baazigar of Bay","BAY Tigers","Challengers CC","Raynor Royals","Daredevils","MINIONS","YAM CC-14","Rising Stars","Skippers","CricKings","Tailenders"));
    static Set<String> divBGroupB = new HashSet<>(Arrays.asList("Alviso Warriors","Barely XI","BraveHearts","Deccan United CC","Champions","Breaking Bats","Extraas","Mavericks","Titans","Veer Jawan","Unstoppable"));
    public static void main(String[] args) throws FileNotFoundException {
        Map<String, Set> map = new HashMap<>();
        for (String team: divAGroupA) {
            map.put(team, divAGroupA);
        }
        for (String team: divAGroupB) {
            map.put(team, divAGroupB);
        }
        for (String team: divBGroupA) {
            map.put(team, divBGroupA);
        }
        for (String team: divBGroupB) {
            map.put(team, divBGroupB);
        }
        try (BufferedReader br = new BufferedReader(new FileReader("/Users/sreevik/Desktop/NACL/Winter_202324_RR.csv"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] fixtures = line.split("\\t");
                if(map.get(fixtures[0]) == map.get(fixtures[3]) || map.get(fixtures[1]) == map.get(fixtures[3])) {
                    System.out.println(line);
                }
            }
            System.out.println("All Good");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
