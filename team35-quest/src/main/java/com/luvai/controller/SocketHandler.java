package com.luvai.controller;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luvai.model.CardList;
import com.luvai.model.Game;
import com.luvai.model.Player;
import com.luvai.model.AdventureCards.AdventureCard;
import com.luvai.model.StoryCards.EventCard;
import com.luvai.model.StoryCards.QuestCard;
import com.luvai.model.StoryCards.StoryCard;
import com.luvai.model.StoryCards.TournamentCard;

@Component
public class SocketHandler extends TextWebSocketHandler {

	List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
	private static final Logger logger = LogManager.getLogger(SocketHandler.class);
	public static Game gameEngine = new Game();
	public static boolean rankSet = true;
	public String BattleInformation = "";
	JsonArray questInformation = new JsonArray();
	public int bonusTestCardControl = 0;
	public boolean sendOnce = true;
	public boolean sentAlready = true;
	public CardList cardFinder = new CardList();

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message)
			throws InterruptedException, IOException {

		JsonObject jsonObject = (new JsonParser()).parse(message.getPayload()).getAsJsonObject();
		// if participant has merlin get preview info if any
		if (jsonObject.has("hasMerlin")) {
			gameEngine.execMerlin(jsonObject);
		}
		// information for logger when choosing anything repeated
		if (jsonObject.has("logInfo")) {
			gameEngine.getLogInfo(jsonObject);
		}
		// send ai to controller
		if (jsonObject.has("AICommand")) {
			gameEngine.AIController.receiveAICommand(jsonObject, session);
		}
		// get player name and set up game players
		if (jsonObject.has("newName")) {
			gameEngine.setupNewPlayer(jsonObject, session);
		}
		// sponsoring quest
		if (jsonObject.has("sponsor_quest")) {
			JsonElement sponsor_quest_answer = jsonObject.get("sponsor_quest");
			if (sponsor_quest_answer.getAsBoolean()) {
				questInformation = new JsonArray();
			}
			gameEngine.getSponsor(jsonObject);
		}

		// json for quest setup info from sponsor
		if (jsonObject.has("questSetupCards")) {
			logger.info("Example of JSON: {}", jsonObject.toString());
			logger.info("Player {} setup {} quest with {}", gameEngine.current_quest.sponsor.getName(),
					gameEngine.storyDeck.faceUp.getName(), jsonObject.get("questSetupCards").toString());
			questInformation.add(jsonObject);
			sendToAllSessions(gameEngine, "questSetupCards" + jsonObject.get("questSetupCards").toString());
			gameEngine.current_quest.parseQuestInfo(jsonObject);
		}

		// if out of stages for quest
		if (jsonObject.has("outOfStages")) {
			Winning();
		}
		// json for accepting/decline participating in quest
		if (jsonObject.has("participate_quest")) {
			gameEngine.current_quest.getNewParticipants(jsonObject, session);
		}

		// json for accepting/decline participating in tournament
		if (jsonObject.has("participate_tournament")) {
			gameEngine.current_tournament.getNewTourniePlayers(jsonObject, session);
		}

		// flip new card
		if (jsonObject.has("flipStoryDeck")) {
			flipStoryCard();
		}
		// go to next turn
		if (jsonObject.has("incTurnRoundOver")) {
			gameEngine.incTurn();
			gameEngine.getActivePlayer().session.sendMessage((new TextMessage("undisableFlip")));
		}
		// json for getting participants battle equipment
		if (jsonObject.has("equipment_info")) {
			logger.info("Example of JSON: {}", jsonObject.toString());
			parseQuestInfo(jsonObject);
		}

		// remove extra stage card when test is first
		if (jsonObject.has("removeStageCardFromTest")) {
			removeExtraTestCard(jsonObject);
		}
		// done events
		// concluded event queen's favor
		if (jsonObject.has("doneEventQueensFavor")) {
			gameEngine.current_event.doneEventQueensFavor();
		}
		// concluded event prosperity throughout realm
		if (jsonObject.has("doneEventProsperity")) {
			gameEngine.current_event.doneProsperity();
		}
		// concluded event kings call to arms
		if (jsonObject.has("doneEventKingsCallToArms")) {
			gameEngine.current_event.doneKingsCallToArms();
		}
		// get tournie info
		if (jsonObject.has("tournament_info")) {
			gameEngine.current_tournament.parseTournieInfo(jsonObject);
		}
		// rigged game
		if (jsonObject.has("riggedGame")) {
			int version = jsonObject.get("riggedGame").getAsInt();
			if (version == 42) {
				logger.info("Setting up rigged game");
				gameEngine.riggedGame = 42;
				gameEngine.storyDeck.initRiggedStoryDeck(gameEngine.riggedGame);
			}
			if (version == 43) {
				logger.info("Setting up rigged game");
				gameEngine.riggedGame = 43;
				gameEngine.storyDeck.initRiggedStoryDeck(gameEngine.riggedGame);
			}

		}
		// print all gameEngine players for requested client
		if (jsonObject.has("print")) {
			printPlayers(session);
		}
		// discarding cards
		if (jsonObject.has("discard")) {
			String discard = jsonObject.get("discard").toString();
			Player p = getPlayerFromSession(session);
			p.discard(discard);
		}
		// validation of connection and decks
		if (jsonObject.has("proof")) {
			validateDecks(session);
		}
		// setting up ai player
		if (jsonObject.has("AI")) {
			gameEngine.AIController.setupNewAIPlayer(jsonObject, session);
		}
	}

	// removes extra given card before stage at conclusion of test if dropped out
	private void removeExtraTestCard(JsonObject jsonObject) {
		Player p = gameEngine.getPlayerFromName(jsonObject.get("name").getAsString());
		String cardName = jsonObject.get("removeStageCardFromTest").getAsString();
		String trimmedName = cardName.replace("http://" + ip.getHostAddress() + ":8080/resources/images/", "");
		if (trimmedName.contains("all.png"))
			return;
		trimmedName = trimmedName.substring(4);
		trimmedName = trimmedName.substring(0, trimmedName.length() - 4);
		trimmedName = trimmedName.replaceAll("%20", " ");
		p.discard(trimmedName);
		gameEngine.updateStats();

	}

	// get all quest information and parses the results -> sending relvant display
	// info back to clients & executing game logic
	private void parseQuestInfo(JsonObject jsonObject) throws IOException {
		if (jsonObject.get("isTest").getAsBoolean()) {
			gameEngine.current_quest.initialStageForTest = gameEngine.current_quest.currentStage;
			gameEngine.current_quest.parseEquipmentInfo(jsonObject);
			questInformation.add(jsonObject);

			if (gameEngine.current_quest.participants.size() == 1) {
				if (gameEngine.current_quest.toDiscardAfterTest.size() == 0) {
					logger.info("Player {} dropped out of {} test",
							gameEngine.current_quest.getCurrentParticipant().getName(),
							gameEngine.storyDeck.faceUp.getName());
					gameEngine.current_quest.participants.remove(gameEngine.current_quest.getCurrentParticipant());
					Losing();
					return;
				}
				logger.info("There is only one participant left in quest test, automatic minimum bid pass");
				gameEngine.current_quest.getCurrentParticipant()
						.discardPlayer(gameEngine.current_quest.getCurrentParticipant().testDiscardList);
				String aiRemove = "";
				for (int i = 0; i < gameEngine.current_quest.getCurrentParticipant().testDiscardList.size(); i++) {
					System.out.println(gameEngine.current_quest.getCurrentParticipant().testDiscardList.get(i));
					aiRemove += gameEngine.current_quest.getCurrentParticipant().testDiscardList.get(i) + ";";
				}
				if (gameEngine.getCurrentParticipant().isAI()) {
					gameEngine.getCurrentParticipant().session
							.sendMessage(new TextMessage("AIRemoveFromScreen" + aiRemove));
				}
				gameEngine.current_quest.getCurrentParticipant().testDiscardList.clear();
				bonusTestCardControl = 1;
				String testBonusReplacement = "";
				for (String s : gameEngine.current_quest.getCurrentParticipant().replaceBonusBidsList) {
					testBonusReplacement += s + ";";
				}
				testBonusReplacement += "null";
				gameEngine.current_quest.getCurrentParticipant().session
						.sendMessage(new TextMessage("PickupCardsTestBonus" + testBonusReplacement));
				gameEngine.updateStats();

				gameEngine.current_quest.currentStage++;
				sendToAllParticipants(gameEngine, "incStage");
				logger.info("Going to stage {}", gameEngine.current_quest.currentStage);
				if (gameEngine.current_quest.currentStage > gameEngine.current_quest.currentQuest.getStages()) {
					Winning();
					return;
				}
				gameEngine.current_quest.pickupBeforeStage();
				gameEngine.current_quest.getCurrentParticipant().session
						.sendMessage(new TextMessage("ChooseEquipment"));
				return;
			}
			if (gameEngine.current_quest.toDiscardAfterTest.size() == 0) {
				if (jsonObject.has("oldBids")) {
					for (AdventureCard a : gameEngine.getCurrentParticipant().getHand()) {
						a.getName();
					}

					JsonArray oldBids = (JsonArray) jsonObject.get("oldBids");
					String replaceTestCards = "";
					for (int i = 0; i < oldBids.size(); i++) {
						JsonElement temp = oldBids.get(i);
						replaceTestCards += temp.toString() + ";";
					}
					gameEngine.getCurrentParticipant().session
							.sendMessage(new TextMessage("replaceTestCards" + replaceTestCards));

					String[] replaceTestCardArr = replaceTestCards.split(";");
					String removeExtra = replaceTestCardArr[0].replaceAll(";", "");
					removeExtra = removeExtra.replaceAll("\"", "");

					for (AdventureCard a : gameEngine.getCurrentParticipant().getHand()) {
						if (a.getName().equals(removeExtra)) {
							gameEngine.getCurrentParticipant().getHand().remove(a);
							break;
						}
					}

				}
				gameEngine.current_quest.participants.remove(gameEngine.current_quest.getCurrentParticipant());
				gameEngine.updateStats();
			}
			sendToAllSessions(gameEngine, "allPlayerQuestInfo" + questInformation.toString());
			if (gameEngine.current_quest.participants.size() == 1) {
				if (gameEngine.current_quest.currentStage > gameEngine.current_quest.currentQuest.getStages()) {
					Winning();
					return;
				} else {
					if (gameEngine.current_quest.participants.size() == 1
							&& gameEngine.current_quest.getCurrentParticipant().testDiscardList.size() != 0) {
						sendToAllSessions(gameEngine, "incStage");
						gameEngine.current_quest.currentStage++;
					}

					gameEngine.current_quest.getCurrentParticipant()
							.discardPlayer(gameEngine.current_quest.getCurrentParticipant().testDiscardList);
					String aiRemove = "";
					for (int i = 0; i < gameEngine.current_quest.getCurrentParticipant().testDiscardList.size(); i++) {

						aiRemove += gameEngine.current_quest.getCurrentParticipant().testDiscardList.get(i) + ";";
					}
					if (gameEngine.getCurrentParticipant().isAI()) {
						gameEngine.getCurrentParticipant().session
								.sendMessage(new TextMessage("AIRemoveFromScreen" + aiRemove));
					}
					gameEngine.current_quest.getCurrentParticipant().testDiscardList.clear();

					bonusTestCardControl = 1;
					String testBonusReplacement = "";
					for (String s : gameEngine.current_quest.getCurrentParticipant().replaceBonusBidsList) {

						testBonusReplacement += s + ";";
					}
					testBonusReplacement += "null";
					gameEngine.current_quest.getCurrentParticipant().session
							.sendMessage(new TextMessage("PickupCardsTestBonus" + testBonusReplacement));
					gameEngine.updateStats();

				}
			}

			String bidMaker = gameEngine.getCurrentParticipant().getName();

			gameEngine.current_quest.incTurn();

			if (gameEngine.current_quest.originalBid > gameEngine.current_quest.currentMinBid) {
			} else {

				logger.info("Sending Player {} bid to sponsor and other participants", bidMaker);
				sendToAllParticipants(gameEngine,
						"whoBidded" + bidMaker + "#" + gameEngine.current_quest.currentMinBid);
				sendToSponsor(gameEngine, "whoBidded" + bidMaker + "#" + gameEngine.current_quest.currentMinBid);
				sendToAllSessions(gameEngine, "updateMinBid" + gameEngine.current_quest.currentMinBid);

			}

			if (gameEngine.current_quest.currentQuestInfo[gameEngine.current_quest.currentStage - 1].contains("Test")) {

			} else {
				gameEngine.current_quest.pickupBeforeStage();
			}

			if (gameEngine.current_quest.participants.size() == 1) {

				logger.info("Player {} won test in {} quest, advancing to stage {}",
						gameEngine.current_quest.getCurrentParticipant().getName(),
						gameEngine.storyDeck.faceUp.getName(), gameEngine.current_quest.currentStage);
				logger.info("Informing players that player {} is winner of test",
						gameEngine.current_quest.getCurrentParticipant().getName());
				sendToAllSessions(gameEngine,
						"testWinner" + gameEngine.current_quest.getCurrentParticipant().getName());
			}
			gameEngine.getCurrentParticipant().session.sendMessage(new TextMessage("ChooseEquipment"));
			return;
		}
		gameEngine.current_quest.equipmentTracker++;

		gameEngine.current_quest.parseEquipmentInfo(jsonObject);
		questInformation.add(jsonObject);
		if (gameEngine.current_quest.equipmentTracker == gameEngine.current_quest.getParticipants().size()) {
			gameEngine.current_quest.equipmentTracker = 0;
			logger.info("All players have chosen equipment for stage {} of {} quest which is a test: {}",
					gameEngine.current_quest.currentStage, gameEngine.storyDeck.faceUp.getName(),
					jsonObject.get("isTest").getAsBoolean());
			logger.info("Updating GUI stats for all players");
			gameEngine.updateStats();
			sendToAllSessions(gameEngine, "allPlayerQuestInfo" + questInformation.toString());
			String playerPoints = "";
			for (int i = 0; i < gameEngine.current_quest.participants.size(); i++) {
				playerPoints += gameEngine.current_quest.participants.get(i).getName() + "#"
						+ gameEngine.current_quest
								.calculatePlayerPoints(gameEngine.current_quest.participants.get(i).getName())
						+ "#" + gameEngine.current_quest.participants.get(i).getRank().getStringFile() + ";";
			}
			sendToAllSessions(gameEngine, "playerPointString" + playerPoints);

			gameEngine.current_quest.calculateStageOutcome(playerPoints, questInformation);
			return;
		}

	}

	// returns all player names when all have joined
	private void printPlayers(WebSocketSession session) throws IOException {
		session.sendMessage(new TextMessage("All players:\n"));
		String clientsString = "clientsString";
		for (Player p : gameEngine.players) {
			clientsString += "ID: " + p.id + " Name: " + p.getName() + "\n";
		}
		session.sendMessage(new TextMessage(clientsString));

	}

	// print deck proof
	private void validateDecks(WebSocketSession session) throws IOException {
		logger.info("ADVENTURE DECK PROOF:");
		int i = 1;
		for (AdventureCard a : gameEngine.adventureDeck.cards) {
			logger.info("{}. {}", i, a.getName());
			i++;
		}
		//
		logger.info("STORY DECK PROOF:");
		int j = 1;
		for (StoryCard s : gameEngine.storyDeck.cards) {
			logger.info("{}. {}", j, s.getName());
			j++;
		}

		// output player name in console
		if (getPlayerFromSession(session) == null) {
		} else {
			getPlayerFromSession(session).session
					.sendMessage(new TextMessage("You are " + getPlayerFromSession(session).getName()));
		}

	}

	// function used when participant has won quest
	@SuppressWarnings("unused")
	public void Winning() throws IOException {
		boolean sendOnce = true;
		String winners = "";
		for (Player p : gameEngine.current_quest.participants) {

			winners += p.getName() + ",";
		}
		if (winners.equals("")) {
			Losing();
			return;
		}
		logger.info("Player(s) {} are the winners of {} quest sponsored by {}, receiving {} shields", winners,
				gameEngine.storyDeck.faceUp.getName(), gameEngine.current_quest.sponsor.getName(),
				gameEngine.current_quest.currentQuest.getStages());
		for (int i = 0; i < gameEngine.players.size(); i++) {

			for (int j = 0; j < gameEngine.current_quest.participants.size(); j++) {

				if (gameEngine.players.get(i).getName()
						.equals(gameEngine.current_quest.participants.get(j).getName())) {
					if (gameEngine.current_quest.shieldSent) {
					} else {
						gameEngine.current_quest.participants.get(j)
								.giveShields(gameEngine.current_quest.currentQuest.getStages());
						gameEngine.current_quest.participants.get(j).session.sendMessage(new TextMessage("Getting "
								+ gameEngine.current_quest.currentQuest.getStages() + " shields for winning quest"));
						logger.info("Giving {} shields to {}", gameEngine.current_quest.currentQuest.getStages(),
								gameEngine.current_quest.participants.get(j).getName());
						if (Game.KingsRecognition) {
							logger.info("Event card King's Recognition is in play, player {} gets 2 extra shields",
									gameEngine.current_quest.participants.get(j).getName());
							gameEngine.current_quest.participants.get(j).giveShields(2);
							Game.KingsRecognition = false;
						}

					}

					gameEngine.current_quest.getCurrentParticipant()
							.discardPlayer(gameEngine.current_quest.getCurrentParticipant().testDiscardList);

					for (int k = 0; k < gameEngine.current_quest.getCurrentParticipant().testDiscardList.size(); k++) {
					}
					gameEngine.current_quest.getCurrentParticipant().testDiscardList.clear();
					String testBonusReplacement = "";
					for (String s : gameEngine.current_quest.getCurrentParticipant().replaceBonusBidsList) {
						testBonusReplacement += s + ";";
					}
					testBonusReplacement += "null";
					if (bonusTestCardControl == 1) {
					} else {
						gameEngine.current_quest.getCurrentParticipant().session
								.sendMessage(new TextMessage("PickupCardsTestBonus" + testBonusReplacement));
						bonusTestCardControl = 0;
					}
					gameEngine.updateStats();

				} else {
					gameEngine.players.get(i).session.sendMessage(new TextMessage("QuestOverWaitForSponsor"));
					if (gameEngine.players.get(i).equals(gameEngine.current_quest.sponsor)) {
						String temp = "";
						String tempNames = "";
						int cardTracker = 0;

						for (int k = gameEngine.current_quest.sponsor.getHandSize(); k < 12
								+ gameEngine.current_quest.currentQuest.getStages(); k++) {
							cardTracker++;
							AdventureCard newCard = gameEngine.adventureDeck.flipCard();
							temp += newCard.getStringFile() + ";";
							tempNames += newCard.getName() + " ";
							gameEngine.current_quest.sponsor.getHand().add(newCard);
							sendOnce = true;
						}
						if (sendOnce) {
							sentAlready = false;
							logger.info(
									"Player {} who sponsored {} quest is receiving {} card ({}) due to"
											+ " sponsoring quest",
									gameEngine.current_quest.sponsor.getName(), gameEngine.storyDeck.faceUp.getName(),
									cardTracker, tempNames);
							logger.info("Player {} has {} cards, will be prompted to discard",
									gameEngine.current_quest.sponsor.getName(),
									gameEngine.current_quest.sponsor.getHand().size());
							gameEngine.current_quest.sponsor.session
									.sendMessage(new TextMessage("SponsorPickup" + temp));
							cardTracker = 0;
							sendOnce = false;
							logger.info("Updating GUI stats for all players");
							gameEngine.updateStats();
							for (Player p : gameEngine.players) {
								AdventureCard amour = p.getAmourCard();
								if (amour == null) {
								} else {

									p.unequipAmour();
									logger.info("Player {} is unequipping the amour used in last quest", p.getName());
								}
							}

						}

					}

				}
			}
			if (gameEngine.current_quest.participants.size() == 1) {
				if (sentAlready) {
					gameEngine.current_quest.sponsorPickup();
					sentAlready = false;
				}
			}
			gameEngine.current_quest.shieldSent = false;
			logger.info("Updating GUI stats for all players");
			gameEngine.updateStats();
			sendOnce = true;
			return;
		}
	}

	// functino executed when all players lose in quest
	public void Losing() throws IOException {
		logger.info("All players defeated in {} quest sponsored by {}", gameEngine.storyDeck.faceUp.getName(),
				gameEngine.current_quest.sponsor.getName());
		sendToAllSessions(gameEngine, "QuestOverWaitForSponsor");
		String temp = "";
		String tempNames = "";
		int cardTracker = 0;
		for (int i = gameEngine.current_quest.sponsor.getHandSize(); i < 12
				+ gameEngine.current_quest.currentQuest.getStages(); i++) {
			cardTracker++;
			AdventureCard newCard = gameEngine.adventureDeck.flipCard();
			temp += newCard.getStringFile() + ";";
			tempNames += newCard.getName() + ", ";
			gameEngine.current_quest.sponsor.getHand().add(newCard);
		}
		logger.info("Player {} who sponsored {} quest is receiving {} card ({}) due to" + " sponsoring quest",
				gameEngine.current_quest.sponsor.getName(), gameEngine.storyDeck.faceUp.getName(), cardTracker,
				tempNames);
		logger.info("Player {} has {} cards, will be prompted to discard", gameEngine.current_quest.sponsor.getName(),
				gameEngine.current_quest.sponsor.getHand().size());
		gameEngine.current_quest.sponsor.session.sendMessage(new TextMessage("SponsorPickup" + temp));
		cardTracker = 0;
		String update = gameEngine.getPlayerStats();
		sendToAllSessions(gameEngine, "updateStats" + update);
		for (Player p : gameEngine.players) {
			AdventureCard amour = p.getAmourCard();
			if (amour == null) {
			} else {

				p.unequipAmour();
				logger.info("Player {} is unequipping the amour used in last quest", p.getName());
			}
		}
	}

	// flip story deck card for new turn
	public static void flipStoryCard() throws IOException {
		logger.info("Updating GUI stats for all players");
		gameEngine.updateStats();
		gameEngine.storyDeck.flipCard();

		logger.info("Player {} is flipping new card from story deck: {}", gameEngine.getActivePlayer().getName(),
				gameEngine.storyDeck.faceUp.getName());
		logger.info("New story card {} is being rendered on player screens", gameEngine.storyDeck.faceUp.getName());
		sendToAllSessions(gameEngine, ("flipStoryDeck" + gameEngine.storyDeck.faceUp.toString()));
		if (gameEngine.storyDeck.faceUp instanceof QuestCard) {
			gameEngine.roundInitiater = gameEngine.getActivePlayer();
			logger.info("Asking Player {} to sponsor quest {}", gameEngine.getActivePlayer().getName(),
					gameEngine.storyDeck.faceUp.getName());
			gameEngine.getActivePlayer().session.sendMessage(new TextMessage("sponsorQuest"));
		}
		if (gameEngine.storyDeck.faceUp instanceof TournamentCard) {
			gameEngine.current_tournament = new TournamentController(gameEngine, gameEngine.storyDeck.faceUp);
			gameEngine.roundInitiater = gameEngine.getActivePlayer();
			gameEngine.getActivePlayer().session.sendMessage(new TextMessage("participateTournament"));
		}
		if (gameEngine.storyDeck.faceUp instanceof EventCard) {
			gameEngine.newEvent(gameEngine, gameEngine.getActivePlayer(), (EventCard) gameEngine.storyDeck.faceUp);
		}
	}

	// set ip address on client side so app is dynamic and can be used anywhere ->
	// prompt client to enter name
	InetAddress ip;

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		try {
			ip = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {

			e.printStackTrace();
		}
		session.sendMessage(new TextMessage("currentIP" + ip.getHostAddress()));
		if (session.getId().equals("0"))
			session.sendMessage(new TextMessage("showRigger"));
		logger.info("New player attempting to connect...");
		if (gameEngine.players.size() == 4) {
			session.sendMessage(new TextMessage("Too many players, sorry. Being disconnected."));
			logger.info("New player got denied entry to game, already full");
			session.close();
			return;
		} else {
			logger.info("Player from session#{} connected", session.getId());
			session.sendMessage(new TextMessage("Welcome> enter your nickname then press send"));
			sessions.add(session);

		}
	}

	// send to current active player
	public static void sendToActivePlayer(Game gameEngine, String message) throws IOException {
		Player temp = gameEngine.getActivePlayer();
		temp.session.sendMessage(new TextMessage(message));
	}

	// send message to all players
	public static void sendToAllSessions(Game gameEngine, String message) {
		for (Player p : gameEngine.players) {
			try {
				p.session.sendMessage(new TextMessage(message));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// get current player
	public Player getPlayerFromSession(WebSocketSession s) {
		for (int i = 0; i < gameEngine.players.size(); i++) {
			if (s.getId().equals(gameEngine.players.get(i).session.getId())) {
				return gameEngine.players.get(i);
			}
		}
		return null;
	}

	// send to non participants for QUESTS
	public void sendToNonParticipants(Game gameEngine, String message) throws IOException {
		ArrayList<Player> temp = new ArrayList<Player>();
		for (int i = 0; i < gameEngine.players.size(); i++) {
			temp.add(gameEngine.players.get(i));
		}
		for (int i = 0; i < temp.size(); i++) {
			if (temp.get(i).equals(gameEngine.current_quest.sponsor))
				temp.remove(i);
		}

		for (int j = 0; j < gameEngine.current_quest.participants.size(); j++) {
			for (int i = 0; i < temp.size(); i++) {
				if (temp.isEmpty())
					return;
				if (temp.get(i).equals(gameEngine.current_quest.participants.get(j)))
					temp.remove(i);

			}
		}

		for (int i = 0; i < temp.size(); i++) {
			temp.get(i).session.sendMessage(new TextMessage(message));
		}

	}

	// send to all participants for QUEST
	public void sendToAllParticipants(Game gameEngine, String message) throws IOException {
		for (Player p : gameEngine.current_quest.participants) {
			p.session.sendMessage(new TextMessage(message));
		}
	}

	// send to current participant for QUEST
	public void sendToCurrentParticipant(Game gameEngine, String message) throws IOException {
		Player player = gameEngine.current_quest.getCurrentParticipant();
		player.session.sendMessage((new TextMessage(message)));
	}

	// send to next participant for QUEST
	public void sendToNextParticipant(Game gameEngine, String message) throws IOException {
		Player player = gameEngine.current_quest.getNextParticipant();
		player.session.sendMessage(new TextMessage(message));
	}

	public void sendToSponsor(Game gameEngine, String message) throws IOException {
		Player player = gameEngine.current_quest.sponsor;
		player.session.sendMessage(new TextMessage(message));
	}

	// send message to next player (not quest)
	public void sendToNextPlayer(Game gameEngine, String message) throws IOException {
		Player player = gameEngine.getNextPlayer();
		player.session.sendMessage(new TextMessage(message));
	}

	// sends to all except parameter
	public void sendToAllPlayersExcept(Game gameEngine, Player p, String message) throws IOException {
		ArrayList<Player> tempList = new ArrayList<Player>();
		for (int i = 0; i < gameEngine.players.size(); i++) {
			if (gameEngine.players.get(i).equals(p)) {
			} else {
				tempList.add(gameEngine.players.get(i));
			}
		}
		for (Player player : tempList) {
			player.session.sendMessage(new TextMessage(message));
		}
	}

	// send to all players except that in current turn
	public static void sendToAllSessionsExceptCurrent(Game gameEngine, WebSocketSession session, String message)
			throws IOException {
		ArrayList<Player> tempList = new ArrayList<Player>();
		for (int i = 0; i < gameEngine.players.size(); i++) {
			if (gameEngine.players.get(i).id == session.getId()) {

			} else {
				tempList.add(gameEngine.players.get(i));
			}
		}
		for (Player p : tempList) {
			p.session.sendMessage(new TextMessage(message));
		}
	}

}// end of class