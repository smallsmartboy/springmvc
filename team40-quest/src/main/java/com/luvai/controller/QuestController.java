package com.luvai.controller;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.luvai.model.Card;
import com.luvai.model.CardList;
import com.luvai.model.Game;
import com.luvai.model.Player;
import com.luvai.model.PointArray;
import com.luvai.model.AdventureCards.AdventureCard;
import com.luvai.model.AdventureCards.AllyCard;
import com.luvai.model.AdventureCards.AmourCard;
import com.luvai.model.AdventureCards.FoeCard;
import com.luvai.model.AdventureCards.TestCard;
import com.luvai.model.AdventureCards.WeaponCard;
import com.luvai.model.StoryCards.QuestCard;

public class QuestController extends SocketHandler {
	private static final Logger logger = LogManager.getLogger(QuestController.class);
	Game gameEngine;
	Player sponsor;
	public ArrayList<Player> participants;
	QuestCard currentQuest;
	public ArrayList<FoeCard> QuestFoes;
	public ArrayList<TestCard> QuestTests;
	public int currentMinBid = 0;
	public int participantTurns = 0;
	public int currentStage = 1;
	public int initialPSize = 0;
	public Player firstQuestPlayer;
	ArrayList<String> toDiscardAfterTest;

	public QuestController(Game g, Player s, QuestCard q) throws IOException {
		logger.info("Initiating new quest {} sponsored by {}", q.getName(), s.getName());
		participants = new ArrayList<Player>();
		QuestFoes = new ArrayList<FoeCard>();
		QuestTests = new ArrayList<TestCard>();
		gameEngine = g;
		sponsor = s;
		currentQuest = q;
		setupQuest();
	}

	public void placeBids(JsonObject json) {
		JsonElement player_bids = json.get("equipment_info");
		Type listType = new TypeToken<List<String>>() {
		}.getType();
		List<String> bidList = new Gson().fromJson(player_bids, listType);
		toDiscardAfterTest = new ArrayList<String>(bidList);
		calculateNumBids(bidList);
		ArrayList<String> tempDiscards = new ArrayList<String>(bidList);
		calculateTestDiscards(tempDiscards);
	}

	// get test discards - bids
	public void calculateTestDiscards(ArrayList<String> discardList) {
		int bonusBids = 0;
		System.out.println(discardList);
		for (int i = 0; i < discardList.size(); i++) {
			AdventureCard tempCard = getCardFromName(discardList.get(i));
			if (tempCard instanceof AmourCard) {
				bonusBids += 1;
			}
			if (tempCard instanceof AllyCard) {
				AllyCard current_ally = (AllyCard) tempCard;
				bonusBids += current_ally.getBid();
			}
		}
		System.out.println(toDiscardAfterTest);
		System.out.println("Bonus bids" + bonusBids);
		for (int i = 0; i < bonusBids; i++) {
			toDiscardAfterTest.remove(0);
			if (toDiscardAfterTest.isEmpty())
				break;
		}
		System.out.println(gameEngine.getCurrentParticipant().getName());
		System.out.println(gameEngine.getCurrentParticipant().getHandSize());
		System.out.println(toDiscardAfterTest);
	}

	// adjust player for points with chosen equipment
	public void equipPlayer(JsonObject json) {
		JsonElement player_equipment = json.get("equipment_info");
		Type listType = new TypeToken<List<String>>() {
		}.getType();
		List<String> equipmentList = new Gson().fromJson(player_equipment, listType);

		for (int i = 0; i < equipmentList.size(); i++) {
			AdventureCard tempCard = getCardFromName(equipmentList.get(i));
			if (tempCard instanceof AllyCard) {

				gameEngine.getCurrentParticipant().getAllies().add((AllyCard) tempCard);
			}
			if (tempCard instanceof WeaponCard) {
				gameEngine.getCurrentParticipant().getWeapons().add((WeaponCard) tempCard);
			}
			if (tempCard instanceof AmourCard) {
				gameEngine.getCurrentParticipant().setAmourCard(tempCard);
			}
		}
		ArrayList<String> remove = new ArrayList<String>(equipmentList);
		gameEngine.getCurrentParticipant().discardPlayer(remove);
		calculatePlayerPoints();
		gameEngine.getCurrentParticipant().getWeapons().clear();
		String update = gameEngine.getPlayerStats();
		sendToAllSessions(gameEngine, "updateStats" + update);
	}

	public void initiateQuestAI(ArrayList<Card> aiQuestcards) {
		ArrayList<String> cardToRemove = new ArrayList<String>();
		for (int i = 0; i < aiQuestcards.size(); i++) {
			cardToRemove.add(aiQuestcards.get(i).getName() + i);
			// System.out.println(aiQuestcards.get(i).getName() + i);
		}
		for (int i = 0; i < aiQuestcards.size(); i++) {
			if (aiQuestcards.get(i) instanceof FoeCard) {
				FoeCard f = (FoeCard) aiQuestcards.get(i);
				for (int j = 0; j < f.getWeapons().size(); j++) {
					cardToRemove.add(f.getWeapons().get(j).getName() + i);
					// System.out.println(f.getWeapons().get(j).getName() + i);
				}
			}

		}
		String logString = "";
		for (String s : cardToRemove) {
			String str = s.substring(0, s.length() - 1);
			logString += str + ", ";
		}
		logger.info("Player {} setup {} quest with {}", gameEngine.getActivePlayer().getName(),
				gameEngine.storyDeck.faceUp.getName(), logString);
		String jsonOutput = "currentQuestInfo" + cardToRemove;
		sendToAllSessions(gameEngine, jsonOutput);

		for (Card c : aiQuestcards) {
			if (c instanceof FoeCard)
				QuestFoes.add((FoeCard) c);
			if (c instanceof TestCard)
				QuestTests.add((TestCard) c);
		}

		calculateFoeBattlePoints();
	}

	// initiate foes for current quest
	public void initiateQuest(JsonObject json) {
		JsonElement quest_cards = json.get("QuestSetupCards");
		Type listType = new TypeToken<List<String>>() {
		}.getType();
		List<String> questCardList = new Gson().fromJson(quest_cards, listType);
		// System.out.println(questCardList);
		for (int k = 0; k < questCardList.size(); k++) {
			// System.out.println("in loop");
			Card tempCard = getCardFromName(questCardList.get(k));
			// System.out.println(tempCard.getName());
			if (tempCard instanceof TestCard) {
				// System.out.println("adding test");
				QuestTests.add((TestCard) tempCard);
			}
			if (tempCard instanceof FoeCard) {
				QuestFoes.add((FoeCard) tempCard);
			}
			if (tempCard instanceof WeaponCard) {
				QuestFoes.get(QuestFoes.size() - 1).getWeapons().add((WeaponCard) tempCard);
			}

		}

		for (FoeCard f : QuestFoes) {
			System.out.println(f.getName());
			for (WeaponCard w : f.getWeapons()) {
				System.out.println(w.getName());
			}
		}
		calculateFoeBattlePoints();
	}

	public void calculateNumBids(List<String> bids) {
		Player currentPlayer = gameEngine.getCurrentParticipant();
		int tempBids = bids.size();
		currentMinBid = tempBids;
		sendToAllSessions(gameEngine, "currentPlayerBids" + tempBids + ";" + currentPlayer.getName());
	}

	public void calculatePlayerPoints() {
		Player currentPlayer = gameEngine.getCurrentParticipant();
		int tempPts = currentPlayer.getBattlePoints();

		String cardNames = "";
		if (currentPlayer.getAmourCard() != null) {
			tempPts += 10;
			cardNames += currentPlayer.getAmourCard().getName() + "#";
		}
		for (WeaponCard w : currentPlayer.getWeapons()) {
			tempPts += w.getBattlePoints();
			cardNames += w.getName() + "#";
		}
		for (AllyCard a : currentPlayer.getAllies()) {
			tempPts += a.getBattlePoints();
			cardNames += a.getName() + "#";
			// ally bonus points
		}
		// System.out.println(tempPts);
		sendToAllSessions(gameEngine,
				"currentPlayerPoints" + tempPts + ";" + currentPlayer.getRank().getName() + ";" + cardNames);
		tempPts = 0;
		currentPlayer.getWeapons().clear();
		// System.out.println(currentPlayer.getBattlePoints());
		// for (AdventureCard a : currentPlayer.getWeapons())
		// System.out.println(a.getName());
	}

	public void calculateFoeBattlePoints() {
		int tempPts;
		PointArray foePoints = new PointArray(currentQuest.getStages() - 1);
		for (FoeCard f : QuestFoes) {
			tempPts = 0;
			if (currentQuest.getFoe().equals(f.getName()) || currentQuest.getFoe2().equals(f.getName())) {
				tempPts = f.getBonusBattlePoints();
			} else {
				tempPts = f.getBattlePoints();
			}
			for (WeaponCard w : f.getWeapons()) {
				tempPts += w.getBattlePoints();
			}
			foePoints.names.add(f.getName());
			foePoints.points.add(tempPts);
		}
		String temp = foePoints.toString();
		sendToAllSessions(gameEngine, "FoeInfo" + temp);
		PointArray testPoints = new PointArray(currentQuest.getStages() - 1);
		for (TestCard t : QuestTests) {
			testPoints.names.add(t.getName());
			testPoints.points.add(t.getMinBid());
		}
		String temp2 = testPoints.toString();
		sendToAllSessions(gameEngine, "TestInfo" + temp2);
	}

	public AdventureCard getCardFromName(String name) {
		AdventureCard card = null;
		CardList cardList = new CardList();
		for (Card a : cardList.adventureTypes) {
			if (a.getName().equals(name)) {
				card = (AdventureCard) a;
				break;
			}
		}
		return card;

	}

	public Player getCurrentParticipant() {
		if (participants.size() == 1)
			return participants.get(0);
		return participants.get(participantTurns % participants.size());
	}

	public void removeParticipant(String name) {
		for (int i = 0; i < participants.size(); i++) {
			if (participants.get(i).getName().equals(name)) {
				participants.remove(i);
			}
		}
	}

	public Player getNextParticipant() {
		if (participants.size() == 1)
			return participants.get(0);
		return participants.get((participantTurns + 1) % participants.size());
	}

	public void incTurn() {
		this.participantTurns++;
	}

	public void setupQuest() throws IOException {
		logger.info("{} is setting up stages for {} quest", this.sponsor.getName(), this.currentQuest.getName());
		sendToAllSessionsExceptCurrent(gameEngine, sponsor.session, "QuestBeingSetup");
	}

	public ArrayList<Player> getParticipants() {
		return this.participants;
	}

	public void getNewParticipants(JsonObject jsonObject, WebSocketSession session) throws IOException {
		JsonElement participate_quest_answer = jsonObject.get("participate_quest");
		JsonElement name = jsonObject.get("name");

		if (participate_quest_answer.getAsBoolean()) {
			logger.info("Player {} accepted to participate in {} quest sponsored by {}", name.getAsString(),
					gameEngine.storyDeck.faceUp.getName(), gameEngine.current_quest.sponsor.getName());
			gameEngine.current_quest.participants.add(gameEngine.getActivePlayer());
			if (gameEngine.current_quest.participants.size() == 1) {
				gameEngine.current_quest.firstQuestPlayer = gameEngine.getCurrentParticipant();
			}
			gameEngine.incTurn();
			if (gameEngine.getActivePlayer().equals(gameEngine.current_quest.sponsor)) {
				gameEngine.current_quest.initialPSize = gameEngine.current_quest.participants.size();
				gameEngine.getActivePlayer().session.sendMessage(new TextMessage("ReadyToStartQuest"));
				gameEngine.getCurrentParticipant().getHand().add(gameEngine.adventureDeck.flipCard());
				String newCardLink = gameEngine.adventureDeck.faceUp.getStringFile();
				gameEngine.getCurrentParticipant().session.sendMessage(new TextMessage("Choose equipment"));
				gameEngine.getCurrentParticipant().session
						.sendMessage(new TextMessage("pickupBeforeStage" + newCardLink));
				String update = gameEngine.getPlayerStats();
				sendToAllSessions(gameEngine, "updateStats" + update);
				return;
			}
			gameEngine.getActivePlayer().session.sendMessage(new TextMessage("AskToParticipate"));
		} else {
			logger.info("Player {} denied to participate in {} quest sponsored by {}", name.getAsString(),
					gameEngine.storyDeck.faceUp.getName(), gameEngine.current_quest.sponsor.getName());
			gameEngine.incTurn();
			if (gameEngine.getActivePlayer().equals(gameEngine.current_quest.sponsor)) {
				gameEngine.current_quest.initialPSize = gameEngine.current_quest.participants.size();
				if (gameEngine.current_quest.participants.size() == 0) {

					sendToAllSessions(gameEngine, "NoParticipants");
					String temp = "";
					for (int i = gameEngine.getActivePlayer().getHandSize(); i < 12
							+ gameEngine.current_quest.currentQuest.getStages(); i++) {
						AdventureCard newCard = gameEngine.adventureDeck.flipCard();
						temp += newCard.getStringFile() + ";";
						gameEngine.getActivePlayer().getHand().add(newCard);
					}
					gameEngine.getActivePlayer().session.sendMessage(new TextMessage("SponsorPickup" + temp));
					logger.info("No players participated, sponsor {} is replacing cards used to setup {} quest",
							gameEngine.getActivePlayer().getName(), gameEngine.storyDeck.faceUp.getName());
					if (gameEngine.getNextPlayer().isAI())
						sendToNextPlayer(gameEngine, "undisableFlip");
					String update = gameEngine.getPlayerStats();
					sendToAllSessions(gameEngine, "updateStats" + update);
					return;
				}
				gameEngine.adventureDeck.flipCard();
				gameEngine.getCurrentParticipant().getHand().add(gameEngine.adventureDeck.faceUp);
				String newCardLink = gameEngine.adventureDeck.faceUp.getStringFile();
				gameEngine.getCurrentParticipant().session.sendMessage(new TextMessage("Choose equipment"));
				gameEngine.getCurrentParticipant().session
						.sendMessage(new TextMessage("pickupBeforeStage" + newCardLink));
				gameEngine.current_quest.firstQuestPlayer = gameEngine.getCurrentParticipant();
				return;
			}
			gameEngine.getActivePlayer().session.sendMessage(new TextMessage("AskToParticipate"));
		}

	}

}
