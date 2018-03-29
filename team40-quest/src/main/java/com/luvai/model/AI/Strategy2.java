package com.luvai.model.AI;

import java.util.ArrayList;
import java.util.Collections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.luvai.model.Card;
import com.luvai.model.Player;
import com.luvai.model.AdventureCards.AdventureCard;
import com.luvai.model.AdventureCards.AllyCard;
import com.luvai.model.AdventureCards.AmourCard;
import com.luvai.model.AdventureCards.FoeCard;
import com.luvai.model.AdventureCards.TestCard;
import com.luvai.model.AdventureCards.WeaponCard;
import com.luvai.model.StoryCards.QuestCard;

public class Strategy2 extends AbstractAI {
	private static final Logger logger = LogManager.getLogger(Strategy2.class);

	public Strategy2() {
		weaponsList = new ArrayList<WeaponCard>();
		alliesList = new ArrayList<AllyCard>();
		amourList = new ArrayList<AmourCard>();
		foeList = new ArrayList<FoeCard>();
		testList = new ArrayList<TestCard>();

		setStrategyType();
		logger.info("Assigning new AI Player strategy {}", this.Strategy_Type);
	}

	@Override
	public boolean doIParticipateQuest() {
		/*
		logger.info("Strategy2 calculating whether to participate in quest");
		Player current_player = this.gameEngine.getActivePlayer();
		sortCards(current_player);
		QuestCard current_quest = (QuestCard) this.gameEngine.storyDeck.faceUp;
		int stages = current_quest.getStages();
		boolean foeConditionMet = false;
		if (foeList.size() < 2) {
			logger.info("Player {} does not meet foe requirements (condition 2) for participate in {} quest",
					current_player.getName(), current_quest.getName());
			return false;
		}
		FoeCard foe1 = foeList.get(0);
		FoeCard foe2 = foeList.get(1);
		// System.out.println(foe1.getName() + foe2.getName());
		if (foe1.getBattlePoints() <= 25 && foe2.getBattlePoints() <= 25)
			foeConditionMet = true;
		if (foeConditionMet) {
			logger.info("Player {} meet foe requirements (condition 2) to participate in {} quest",
					current_player.getName(), current_quest.getName());
		} else {
			logger.info("Player {} does not meet foe requirements (condition 2) for participate in {} quest",
					current_player.getName(), current_quest.getName());
			return false;
		}
		int totalWeaponBP = 0;
		for (WeaponCard w : weaponsList) {
			totalWeaponBP += w.getBattlePoints();
		}
		int stageToWeaponRatio = totalWeaponBP / stages;
		// System.out.println("line 45" + stageToWeaponRatio);
		if (stageToWeaponRatio >= 10) {
			logger.info("Player {} has enough weapons to sustain {} quest", current_player.getName(),
					current_quest.getName());
			return true;
		}
		logger.info("Not enough weapons, checking for amour");
		if (stageToWeaponRatio + (10 / stages) >= 10) {
			logger.info("Player {} can sustain quest {} with weapons & amour played", current_player.getName(),
					current_quest.getName());
			return true;
		}
		stageToWeaponRatio = stageToWeaponRatio + (10 / stages);
		logger.info("Not strong enough with weapons and amour, checking for allies");
		int totalAllyBP = 0;
		for (AllyCard a : alliesList) {
			totalAllyBP += a.getBattlePoints();
		}
		int stageToAllyRatio = totalAllyBP / stages;
		if (stageToAllyRatio + stageToWeaponRatio >= 10) {
			logger.info("Player {} can sustain quest {} with weapons, amour, & allies played", current_player.getName(),
					current_quest.getName());
			return true;
		}
		logger.info("Player {} cards do not meet conditions needed to play in {} quest", current_player.getName(),
				current_quest.getName());*/
		return false;

	}

	@Override
	public void setStrategyType() {
		this.Strategy_Type = "Strategy2";

	}

	@Override
	public AdventureCard[] getDiscardChoice(Player currentPlayer, int numDiscards) {
		AdventureCard[] discards = new AdventureCard[numDiscards];
		ArrayList<AdventureCard> toDiscard = new ArrayList<AdventureCard>();

		for (AdventureCard a : currentPlayer.getHand()) {
			if (toDiscard.size() == 2)
				break;
			if (a.getBattlePoints() == 0)
				if (toDiscard.contains(a)) {
				} else {
					toDiscard.add(a);
				}
			if (a.getBattlePoints() == -1)
				if (toDiscard.contains(a)) {
				} else {
					toDiscard.add(a);
				}
			if (a.getBattlePoints() < 6)
				if (toDiscard.contains(a)) {
				} else {
					toDiscard.add(a);
				}
		}

		AdventureCard[] test = new AdventureCard[toDiscard.size()];
		toDiscard.toArray(test);

		for (int i = 0; i < numDiscards; i++) {
			discards[i] = test[i];
		}

		return discards;
	}

	@Override
	public boolean doISponsorQuest() {
		logger.info("Strategy2 calculating whether to sponsor quest");
		sortCards(this.gameEngine.getActivePlayer());
		QuestCard current_quest = (QuestCard) this.gameEngine.storyDeck.faceUp;
		int stages = current_quest.getStages();
		ArrayList<Integer> current_player_shields = new ArrayList<Integer>();
		for (Player p : this.gameEngine.players) {
			current_player_shields.add(p.getShields());
		}
		// if someone can evolve rank do not sponsor
		for (int s : current_player_shields) {
			if (s < 5) { // squire
				if (5 - s <= stages) {
					logger.info("A player can evolve if this quest is won, declining to sponsor");
					return false;
				}
			}
			if (s >= 5 && s < 7) { // knight
				if (7 - s <= stages) {
					logger.info("A player can evolve if this quest is won, declining to sponsor");
					return false;
				}
			}
			if (s >= 7 && s <= 10) { // champion knight
				if (10 - s <= stages) {
					logger.info("A player can evolve if this quest is won, declining to sponsor");
					return false;
				}
			}
		}
		logger.info("No players can evolve if this quest is sponsored");
		// check for increase foes
		int foeTracker = 0;
		@SuppressWarnings("unchecked")
		ArrayList<FoeCard> temp = (ArrayList<FoeCard>) foeList.clone();
		for (int i = 0; i < stages; i++) {

			for (int j = 0; j < temp.size(); j++) {
				if (j == temp.size() - 1) {
					break;
				}
				if (temp.get(j).getBattlePoints() != temp.get(j + 1).getBattlePoints()) {
					temp.remove(j);
					foeTracker++;
					break;
				}
			}
		}
		// for (FoeCard f : foeList) {
		// System.out.println(f.getName());
		// }
		if (foeList.size() == 1)
			foeTracker = 1;
		if (foeList.size() == 2) {
			// System.out.println("here");
			if (foeList.get(0).getBattlePoints() != foeList.get(1).getBattlePoints())
				foeTracker = 2;
		}
		if (foeTracker < stages) {
			if (testList.size() + foeTracker >= stages) {
				logger.info("Enough foes to sponsor quest if tests are used");
				return true;
			}
			// System.out.println(testList.size());
			// System.out.println(foeTracker);
			logger.info("Requirements to sponsor quest are not met");
			logger.info("Player {} denied to sponsor quest {}", this.gameEngine.getActivePlayer().getName(),
					this.gameEngine.storyDeck.faceUp.getName());
			return false;
		}
		logger.info("Requirements to sponsor quest met");
		logger.info("Player {} accepted to sponsor quest {}", this.gameEngine.getActivePlayer().getName(),
				this.gameEngine.storyDeck.faceUp.getName());

		return true;
	}

	@Override
	public void setupQuest() {

		Player currentPlayer = this.gameEngine.getActivePlayer();
		sortCards(currentPlayer);
		QuestCard current_quest = (QuestCard) this.gameEngine.storyDeck.faceUp;
		int totalStages = current_quest.getStages();
		int remainingStages = 0;
		ArrayList<Card> finalQuestSetup = new ArrayList<Card>();
		TestCard testCard = null;
		logger.info("{} setting up {} quest ensuring last stage battle has at least 40 points", currentPlayer.getName(),
				current_quest.getName());
		FoeCard topFoe = foeList.get(foeList.size() - 1);
		if (weaponsList.isEmpty()) {
			logger.info("No available weapons for foe to equip");
		} else {
			for (int i = 0; i < weaponsList.size(); i++) {
				topFoe.getWeapons().add(weaponsList.get(i));
				if (topFoe.getBattlePoints() + topFoe.getWeaponPoints() >= 40)
					break;
			}
		}

		finalQuestSetup.add(topFoe);
		if (testList.size() != 0) {
			testCard = testList.get(0);
			finalQuestSetup.add(testCard);
		}
		if (testCard == null) {
			logger.info("Player {} has no tests to use for 2nd last stage of {} quest setup", currentPlayer.getName(),
					current_quest.getName());
			remainingStages = totalStages - 1;
		} else {
			logger.info("Player {} used test for 2nd last stage of {} quest setup", currentPlayer.getName(),
					current_quest.getName());
			remainingStages = totalStages - 2;
		}
		if (remainingStages == 0) {
			logger.info("No more stages to set");
		} else {
			logger.info("Setting remaning stages to low point foes with no weapons");
		}
		for (int i = 0; i < remainingStages; i++) {
			finalQuestSetup.add(foeList.get(i));
		}
		Collections.reverse(finalQuestSetup);
		this.gameEngine.current_quest.initiateQuestAI(finalQuestSetup);
		// System.out.println("Discard ai sponsor here");
		for (Card c : finalQuestSetup) {
			// System.out.println(c.getName());
			currentPlayer.discard(c.getName());
			if (c instanceof FoeCard) {
				for (WeaponCard w : ((FoeCard) c).getWeapons()) {
					// System.out.println(w.getName());
					currentPlayer.discard(w.getName());
				}
			}
		}
	}
} // end of class
