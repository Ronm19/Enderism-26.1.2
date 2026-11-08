package com.ronm19.enderism.codex;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The complete field guide. Every entry is available without player progression or encounters. */
public final class EnderCodex {
    public record Entry(int number, String id, String name, String gender, String habitat, String activity,
                        String description, String avoidance, String escape, String combat) {
        public String key(String field) { return "codex.enderism." + this.id + "." + field; }
        public String numberLabel() { return String.format(java.util.Locale.ROOT, "#%03d", this.number); }
    }

    public static final List<Entry> ENTRIES = List.of(
        entry(1,"brown","Male","Forests","Night",
            "A heavy forest hunter whose intimidating presence makes panic dangerous.",
            "Stay alert beneath forest cover at night.","Find open ground and keep your route clear.","Keep calm, watch its approach, and respect its heavy melee strikes."),
        entry(2,"gray","Female","Pale Garden","Day and night",
            "An amber-eyed stalker of pale oak woodland, using concealment and Blindness to ambush prey.",
            "Watch pale oak cover and listen for nearby movement.","Leave dense woodland before losing your bearings.","Keep the creature in view and prepare for Blindness."),
        entry(3,"arctic","Female","Snowy biomes","Day",
            "A daylight predator adapted to ice and snow. Her freezing attacks can set up a deadly execution.",
            "Watch the snowfields during daylight.","Retreat before freezing pressure builds.","Do not let a stun leave you exposed at close range."),
        entry(4,"fire","Male","Nether Wastes, Crimson Forest; rarely Badlands and Savanna","Day and night",
            "A charred, magma-veined hunter whose Flame Strike burns prey and adds temporary Hunger. Hunger Hex curses a visible target after a warning, while Cinder Burst releases a short-range fire pulse.",
            "Watch for molten markings in hot terrain.","Use cover to interrupt Hunger Hex and move away while Cinder Burst charges.","Prepare for burning melee hits and Hunger. Fire Resistance helps against fire; keep room to evade the burst while its abilities recharge."),
        entry(5,"frost","Female","Snowy biomes","Night",
            "An icy night hunter with heightened cold awareness during snowstorms. Frost Strike briefly paralyzes prey, Frozen Paralysis threatens a visible target after a warning, and Hypothermia releases a local freezing pulse.",
            "Be especially cautious in snowy darkness and storms.","Break sight during Frozen Paralysis and leave the area while Hypothermia charges.","Avoid repeated close-range hits. Recognize her casting pauses and use the recovery between finite paralysis and freezing attacks."),
        entry(6,"forest","Male","Forests and old-growth woodland","Day and night",
            "A foliage-covered hunter that stalks quietly, remembers escape routes, and restrains prey with grasping vines.",
            "Check wooded flanks and listen beyond the canopy.","Move toward open terrain before roots close around you.","Watch for the vine warning and stay out of confined corners."),
        entry(7,"storm","Female","Windswept Hills, Windswept Gravelly Hills, Windswept Forest","Night",
            "An exposed-hill hunter that commands focused lightning, forceful gusts, and a localized tempest. A successful tempest also calls a brief world thunderstorm when the natural weather cycle is enabled.",
            "Watch exposed ridges after nightfall.","Leave the local storm and keep an open escape route.","Move away from strike warnings and expect gusts to disrupt projectiles."),
        entry(8,"sand","Male","Desert","Day and night",
            "A desert stalker that uses a localized sandstorm to conceal pursuit and delivers weak Poison with successful attacks.",
            "Stay alert when blowing sand obscures the desert.","Leave the veiled area and seek clearer sightlines.","Watch for repositioning and avoid repeated poisoned melee hits."),
        entry(9,"silver","Female","Dark Forest","Night",
            "An agile huntress with rapid silver slashes and tactical teleportation. Repeated successful hits temporarily weaken effective armor protection.",
            "Check the shadows and flank routes beneath dark oak canopies.","Use open paths and break contact long enough to escape her search.","Face her repositioning and avoid repeated melee exchanges. Armor Fracture does not itself damage equipment durability."),
        entry(10,"phantom","Male","Windswept Gravelly Hills","Day and night; more dangerous at night",
            "A stealth-oriented Enderman that stalks prey and specializes in sudden attacks from behind. It uses teleportation to reposition and can inflict Darkness and Slowness.",
            "Remain alert in gravelly mountain terrain, especially at night. Watch your surroundings and avoid exposing your back for long periods.",
            "Keep moving toward safer terrain, maintain awareness of possible flank routes, and avoid becoming trapped against steep slopes.",
            "Face the creature when possible, anticipate repositioning, and prepare for delayed Darkness and Slowness effects."),
        entry(11,"dark","Male","Dark Forest","Nighttime only",
            "A dangerous Enderman that combines melee attacks with dark magical projectiles. It blinds its prey and can inflict temporary Wither at controlled intervals.",
            "Stay alert while traveling through Dark Forests at night. Dense trees can conceal the creature's approach.",
            "Use solid cover to interrupt ranged attacks, but remember that Dark Enderman can reposition intelligently.",
            "Be prepared for both melee and projectile attacks. Maintain awareness while blinded, use terrain to block projectiles, and anticipate its Wither attack."),
        entry(12,"infernal","Male","Nether Wastes and Basalt Deltas","Day and night",
            "A horned, extremely dangerous Nether Enderman capable of running across lava, delivering devastating infernal strikes, and calling down a catastrophic meteor bombardment.",
            "Be extremely cautious near lava lakes. Infernal Enderman can cross them.",
            "Avoid narrow routes where its charge is difficult to dodge. During Meteor Cataclysm, watch for meteor impact warnings and move away from threatened areas.",
            "Prepare for severe burning damage, avoid direct charges, and preserve mobility for the final giant meteor. Stay alert after the smaller meteors strike."),
        entry(13,"golden","Female","Badlands","Daytime only for natural spawning",
            "A hostile golden Enderman whose supernatural Midas Touch can impair opponents, while powerful golden magic inflicts severe Slowness and temporary stuns.",
            "Stay alert when exploring Badlands in daylight. Golden Enderman hunts even in full sun.",
            "Keep your distance before she can impair your movement. Use terrain carefully and avoid getting trapped against cliffs or ravines.",
            "Anticipate Midas Touch and heavy Slowness. Watch for Golden Judgment's wind-up, dodge the stun, and use the opening while her abilities recharge."),
        entry(14,"nightmare","Male","Taiga","Nighttime only for natural spawning",
            "A terrifying black Enderman with glowing red eyes. It stalks prey through spruce forests, creates deceptive apparitions, and uses Darkness to inspire fear. Once the true hunt begins, every illusion disappears and the creature commits to relentless physical pursuit.",
            "Be cautious when exploring Taiga at night. Strange silhouettes and red eyes may be illusions, with a real Nightmare Enderman nearby.",
            "Use terrain and distance to break pursuit. Once the real chase begins, focus on escaping the actual creature.",
            "Watch for the change from stalking to physical pursuit. During the real hunt, anticipate direct melee attacks and genuine Enderman teleportation."),
        entry(15,"demon","Male","Crimson Forest (primary), Nether Wastes (secondary)","Day and night",
            "A horned, extremely aggressive Nether Enderman with glowing red eyes and orange pupils. Its repeated claw attacks accumulate supernatural Ash on victims until the buildup erupts in a violent burst of damage.",
            "Be especially careful in Crimson Forests, where this species is most common. Avoid allowing it to repeatedly strike you.",
            "Maintain distance, use terrain carefully, and avoid being trapped during rapid melee sequences. Successive hits build Ash toward an eruption.",
            "Interrupt its offensive pressure. Anticipate Demonic Onslaught, watch for Hellborn Frenzy, and avoid letting Ash reach the eruption threshold."),
        entry(16,"blood","Male","Badlands","Nighttime only for natural spawning",
            "A highly aggressive nocturnal Enderman that combines powerful melee combat with supernatural blood projectiles. More tactically intelligent and aggressive than Demon Enderman, it gains Regeneration after every credited kill.",
            "Be cautious in the Badlands at night. Vulnerable creatures nearby can give this hunter opportunities to renew its Regeneration.",
            "Use terrain to break projectile lines of sight. Be ready for repositioning and a switch to melee pursuit.",
            "Watch for Blood Lance, anticipate aggressive close-range attacks, and prevent additional kills that would refresh Bloodlust."),
        entry(17,"spectral","Female","Taiga","Nighttime only for natural spawning",
            "A ghostlike Enderman that stalks prey through Taiga forests before suddenly materializing nearby. Its supernatural ambush strikes can inflict devastating damage, making it especially dangerous to unsuspecting explorers.",
            "Remain alert in Taiga forests at night. Listen for unusual supernatural sounds and watch for faint ghostly figures between the trees.",
            "Keep moving along clear escape routes. Avoid becoming trapped against dense spruce trees or steep terrain.",
            "Watch for materialization cues and deny a clear opportunity for an empowered ambush. Once she fully manifests, use ordinary defenses while anticipating another reposition."),
        entry(18,"angel","Female","Cherry Grove","Day and night",
            "A mysterious celestial Enderman that inhabits Cherry Groves. Unlike most Endermen, she does not attack players without provocation. She heals and strengthens other Endermen using supernatural blessings, but becomes defensive when players invade her personal space.",
            "Maintain a respectful distance. Do not approach too closely or attack her.",
            "If she becomes defensive, retreat beyond her territorial range and stop attacking. She calms down once she no longer considers you a threat.",
            "If combat becomes unavoidable, anticipate defensive melee strikes and remember that she can heal or strengthen nearby Endermen. Her support abilities do not benefit players."),
        entry(19,"ravager","Unrecorded","Pillager Outposts","Day and night; also participates in raids",
            "A massive, horned, two-legged Enderman-Ravager hybrid associated with Pillager Outposts and raids. Its extraordinary physical strength, charging attacks, and devastating roar make it a dangerous siege combatant.",
            "Exercise caution near Pillager Outposts and during raids. Watch for its enormous horned silhouette and avoid standing directly along open charging paths.",
            "Use terrain and obstacles to interrupt a charging route. Maintain distance from its powerful melee strikes and avoid becoming trapped during raid encounters.",
            "Anticipate heavy knockback, recognize the wind-up for Siege Charge, and prepare for its disruptive Raidbreaker Roar. During raids, pay attention to supporting Illagers rather than focusing only on the Ravager Enderman."),
        entry(20,"enderman_golem","Male","Villages","Day and night",
            "An ancient, colossal Enderman-Iron Golem hybrid that guards villages and protects their residents. His stone-plated fists and Ender-infused strength make him a powerful defender, while respectful players can observe him peacefully.",
            "Respect his personal space and do not attack him or threaten the village's residents.",
            "If he becomes defensive, retreat beyond his territorial range and stop attacking. He calms once the threat has passed.",
            "Recognize the preparation for Colossal Crusher and Titan's Judgment. Give him space, and avoid standing between him and a villager he is moving to protect."),
        entry(21,"void","Male","The End","Day and night",
            "A highly intelligent Enderman adapted to the dangerous floating islands of The End. It uses supernatural teleportation and controlled void energy to manipulate enemy positioning. Its most remarkable trait is an instinctive ability to escape fatal void falls by teleporting back onto safe ground.",
            "Watch for Void Endermen while exploring End islands. Maintain awareness of nearby cliffs, gaps, and narrow platforms.",
            "Use terrain and distance carefully. Be prepared for tactical teleportation and avoid placing yourself where a sudden positional attack could become dangerous.",
            "Anticipate Abyssal Strike, watch for Void Step repositioning, and react to the warning before Abyssal Pull. Knocking the creature into the void is not a reliable way to defeat it, because it can teleport back to safety."),
        entry(22,"bogged","Male","Swamp","Nighttime natural spawning",
            "A terrifying swamp-dwelling Enderman covered in moss and mushrooms, with sections of exposed skeletal anatomy. Its eyes glow bright green when angered. It specializes in powerful Poison attacks, supernatural swamp projectiles, and temporary toxic battlefield effects.",
            "Be cautious when exploring Swamps at night. Watch for its glowing green eyes and avoid unnecessary close-range encounters.",
            "Keep moving and avoid remaining inside its Toxic Mire. Use solid terrain to interrupt incoming poisonous projectiles and create space from melee attacks.",
            "Prepare for heavy Poison effects. Avoid repeated Venomous Grasp hits, watch for Bogged Barrage projectiles, and leave the poisonous area when Toxic Mire activates."),
        entry(23,"skeleton","Male","Plains and Dark Forest","Normal hostile spawning; burns in direct sunlight",
            "A supernatural skeletal Enderman that creates magical arrows without using a bow. Its Arcane Volley scales with the number of nearby players, generating one arrow per player up to a maximum of twenty. Its cursed arrows can freeze, burn, slow, poison, or inflict Wither, making it a dangerous ranged opponent.",
            "Be cautious in Plains and Dark Forest environments, particularly where darkness allows it to remain active without burning. Use natural cover to avoid its ranged attacks.",
            "Break projectile lines of sight using terrain and obstacles. Avoid running across open areas while it prepares a volley.",
            "Watch for Arcane Volley, Cursed Arrow, and Death's Barrage preparations and anticipate elemental effects. A larger group of players causes Skeleton Enderman to generate more arrows, up to twenty. Direct sunlight is one of its key weaknesses."),
        entry(24,"zombie","Male","Plains and Forest","Nighttime natural spawning; burns in direct sunlight",
            "A decayed undead Enderman whose infected bite inflicts Poison and Slowness. When attacked, it alerts nearby Zombie Endermen, allowing them to investigate and coordinate against the aggressor. Its supernatural intelligence makes groups especially dangerous during nighttime encounters.",
            "Exercise caution in Plains and Forests after sunset. A lone Zombie Enderman may have others nearby, and attacking one can alert the group.",
            "Avoid becoming surrounded. Use terrain, distance, and daylight exposure to create opportunities for escape. Remember that its bite can both poison and slow you.",
            "Prepare for Poison and Slowness. Avoid repeated Infected Bite hits, watch for Horde Signal responses and Undead Frenzy, and use the sunlight vulnerability to your advantage where possible."),
        entry(25,"sculk","Female","Deep Dark and Ancient Cities","Underground; day and night",
            "A supernatural Enderman infused with sculk energy. She detects vibrations and investigates their sources, applying Darkness when she identifies a nearby player. Remaining still can help avoid detection at a distance, but anyone within approximately five blocks will be detected and attacked regardless of movement. Her powerful melee attacks inflict Slowness. She is allied with Wardens and does not trigger sculk sensors.",
            "Avoid unnecessary vibrations in the Deep Dark. Sneak carefully, use vibration-dampening materials, and maintain more than five blocks of distance.",
            "Break contact and stop generating vibrations when safely outside close range. Allow her temporary investigation memory to expire rather than constantly revealing your location.",
            "Prepare for Darkness Awakening and Sculk Crusher's powerful slowing melee strikes. Vibrational Pursuit follows detected sources, but she has no projectiles. Nearby Wardens will not attack her."),
        entry(26,"husk","Male","Desert and Badlands","Nighttime natural spawning; does not burn in sunlight",
            "A desiccated undead Enderman that inhabits Deserts and Badlands. Unlike Zombie Enderman, it does not burn in sunlight. Its attacks inflict Weakness, supernatural Hunger, and temporary Nausea, making prolonged confrontations especially dangerous.",
            "Exercise caution in Deserts and Badlands at night. Although Husk Enderman naturally spawns after dark, it may remain dangerous after sunrise.",
            "Avoid repeated Desiccating Strike hits and maintain enough distance to respond to Starvation Curse and Desert Delirium. Do not assume daylight will eliminate the creature.",
            "Prepare for Weakness, Hunger, and Nausea. Desiccating Strike weakens melee attacks, Starvation Curse pressures food reserves, and Desert Delirium causes temporary Nausea. React to curse animations and use ordinary status-effect counterplay. Sunlight will not defeat it."),
        entry(27,"vampire","Female","Dark Forest","Nighttime natural spawning",
            "A highly agile nocturnal Enderman that hunts through Dark Forests using rapid dashes, powerful leaps, and intelligent projectile evasion. Repeated successful attacks allow her to drain health from victims and restore her own. During Blood Fury, her eyes glow brighter and her aggression and damage increase dramatically.",
            "Be cautious in Dark Forests at night. Avoid allowing Vampire Enderman to repeatedly land melee attacks, as successful strikes can build toward life draining.",
            "Use terrain and distance carefully. She can dash, jump high, and evade projectiles, so simple straight-line movement may not be sufficient.",
            "Watch for Crimson Dash, anticipate evasive movement, and avoid extended close-range exchanges that allow Vampiric Drain to trigger. When Blood Fury activates, expect brighter eyes, increased aggression, and stronger attacks until the temporary state expires."),
        entry(28,"wither","Male","Soul Sand Valley, Nether","Any time in the Nether",
            "A terrifying Wither-infused Enderman that inhabits the Soul Sand Valley. Its melee attacks inflict Wither, while its supernatural Skull Barrage launches dangerous Wither skull projectiles. Its most distinctive power, Rose of Decay, places real Wither Roses on suitable ground, creating hazardous areas that make movement and combat increasingly dangerous.",
            "Exercise caution when traveling through Soul Sand Valleys. Watch for dark supernatural particles, Wither skulls, and hazardous Wither Roses.",
            "Use solid terrain to interrupt projectile paths, keep moving to avoid Skull Barrage, and do not walk carelessly through freshly placed Wither Roses.",
            "Prepare for Wither status effects. Avoid repeated Withering Grasp hits, recognize the Skull Barrage casting animation, and reposition when Rose of Decay creates hazardous ground. The creature remains vulnerable to legitimate combat damage."),
        entry(29,"white","Female","Jungle","Uncommon; nighttime natural spawning",
            "An exceptionally intelligent white Enderman with violet eyes that hunts in Jungle environments at night. She relies on patient stalking, advanced target tracking, tactical positioning, and carefully timed physical attacks. Predator's Mark helps her maintain focus on wounded targets, Relentless Tracking improves her investigation of lost enemies, and Apex Hunt temporarily elevates her precision and hunting pressure.",
            "Exercise caution in Jungles after sunset. White Enderman is uncommon, but exceptionally dangerous. Watch for unusual white figures between dense trees and avoid allowing her to approach unnoticed.",
            "Break line of sight and create distance. She can investigate your last known location and search nearby routes, but her tracking is not unlimited. Use terrain intelligently and avoid revealing your position repeatedly.",
            "Avoid repeated close-range hits, watch for Predator's Mark, and recognize the warning before Apex Hunt. Her strongest advantage is intelligent positioning, so unpredictable movement, terrain awareness, and careful timing are essential. Relentless Tracking depends on recent observed evidence and eventually expires."),
        entry(30,"soul_sand","Male","Soul Sand Valley, Nether","Common; no day/night restriction",
              "A supernatural Enderman adapted to the Soul Sand Valley. Soul Sand Enderman moves faster across soul sand and channels blue soul fire through his attacks. His Soulfire Strike burns enemies, Soulflame Eruption creates a powerful localized burst of soul fire, and Soulfire Dominion temporarily fills the surrounding battlefield with hazardous blue flames.",
              "Exercise caution when crossing Soul Sand Valleys. Soul Sand Endermen are relatively common in this habitat and can use the terrain to move faster than their victims.",
              "Avoid relying on soul sand to slow your pursuer. Use safer terrain, maintain distance, and leave hazardous areas when Soulfire Dominion activates.",
              "Prepare for soul-fire attacks. Watch for the warning before Soulflame Eruption, avoid standing in Soulfire Dominion, and remember that the creature gains a movement advantage on soul sand. Appropriate fire protection and tactical positioning can improve survival.")
    );

    private EnderCodex() {}
    private static Entry entry(int n, String id, String gender, String habitat, String activity,
                               String description, String avoidance, String escape, String combat) {
        String base = id.equals("enderman_golem") ? "Enderman Golem" :
                java.util.Arrays.stream(id.split("_")).map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                        .collect(java.util.stream.Collectors.joining(" ")) + " Enderman";
        return new Entry(n,id,base,gender,habitat,activity,description,avoidance,escape,combat);
    }

    public static Map<String,String> translations() {
        Map<String,String> result = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            result.put(entry.key("name"), entry.name()); result.put(entry.key("gender"), entry.gender());
            result.put(entry.key("habitat"), entry.habitat()); result.put(entry.key("activity"), entry.activity());
            result.put(entry.key("description"), entry.description()); result.put(entry.key("avoidance"), entry.avoidance());
            result.put(entry.key("escape"), entry.escape()); result.put(entry.key("combat"), entry.combat());
        }
        return Map.copyOf(result);
    }
}
