package com.luvai.model;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.luvai.controller.AIController;
import com.luvai.controller.EventController;
import com.luvai.controller.QuestController;
import com.luvai.controller.SocketHandler;
import com.luvai.model.AdventureCards.AdventureCard;
import com.luvai.model.Decks.AdventureDeck;
import com.luvai.model.Decks.StoryDeck;
import com.luvai.model.StoryCards.EventCard;
import com.luvai.model.StoryCards.QuestCard;

public class Game {
	private static final Logger logger = LogManager.getLogger(Game.class);
	public ArrayList<Player> players;
	public StoryDeck storyDeck;
	public AdventureDeck adventureDeck;
	private int winCondition;
	public int numTurns = 0;
	public QuestController current_quest;
	public EventController current_event;
	public AIController AIController;
	public Player roundInitiater;
	public int riggedGame = 0;

	public Game() {
		logger.info("\n\n\n\n ****************************** Initialising new game ********************************");
		players = new ArrayList<Player>();
		adventureDeck = new AdventureDeck();
		storyDeck = new StoryDeck();
		AIController = new AIController();
	}

	@SuppressWarnings("serial")
	public ArrayList<AdventureCard> mockHand1 = new ArrayList<AdventureCard>() {
		{
			add(CardList.Boar);
			add(CardList.BlackKnight);
			add(CardList.BeastTest);
			add(CardList.Dagger);
			add(CardList.Amour);
			add(CardList.Horse);
			add(CardList.Saxons);
			add(CardList.Merlin);
			add(CardList.Sword);
			add(CardList.TemptationTest);
			add(CardList.Sword);
			add(CardList.SirGawain);
		}
	};
	@SuppressWarnings("serial")
	public ArrayList<AdventureCard> mockHand2 = new ArrayList<AdventureCard>() {
		{
			add(CardList.SirTristan);
			add(CardList.MorganTest);
			add(CardList.Amour);
			add(CardList.RobberKnight);
			add(CardList.Merlin);
			add(CardList.Horse);
			add(CardList.Dagger);
			add(CardList.Horse);
			add(CardList.Battleax);
			add(CardList.GreenKnight);
			add(CardList.Lance);
			add(CardList.SirLancelot);
		}
	};
	@SuppressWarnings("serial")
	public ArrayList<AdventureCard> mockHand3 = new ArrayList<AdventureCard>() {
		{
			add(CardList.BeastTest);
			add(CardList.Excalibur);
			add(CardList.Giant);
			add(CardList.EvilKnight);
			add(CardList.KingArthur);
			add(CardList.Excalibur);
			add(CardList.Horse);
			add(CardList.Horse);
			add(CardList.Dragon);
			add(CardList.BlackKnight);
			add(CardList.QueenGuinevere);
			add(CardList.SirGalahad);
		}
	};
	@SuppressWarnings("serial")
	public ArrayList<AdventureCard> mockHand4 = new ArrayList<AdventureCard>() {
		{

			// add(CardList.Dagger);
			add(CardList.KingArthur);
			add(CardList.GreenKnight);
			add(CardList.Saxons);
			add(CardList.SaxonKnight);
			add(CardList.Boar);
			add(CardList.QueenIseult);
			add(CardList.MorganTest);
			add(CardList.ValorTest);
			add(CardList.Dagger);
			add(CardList.SirPellinore);
			add(CardList.Amour);
			add(CardList.SirLancelot);
		}
	};

	public void newQuest(Game g, Player s, QuestCard q) throws IOException {
		current_quest = new QuestController(g, s, q);
	}

	public void newEvent(Game g, Player s, EventCard e) throws IOException {
		current_event = new EventController(g, s, e);
	}

	public void incTurn() {
		this.numTurns++;
	}

	public void setName(Player p, String s) {
		p.setName(s);
	}

	public Player getNextPlayer() {
		return players.get((numTurns + 1) % players.size());
	}

	public Player getActivePlayer() {
		return players.get(numTurns % players.size());
	}

	public Player getPrevPlayer() {
		return players.get(((numTurns - 1) % players.size()));
	}

	public Player getPlayerFromName(String name) {
		for (int i = 0; i < this.players.size(); i++) {
			if (name.equals(this.players.get(i).getName())) {
				return this.players.get(i);
			}
		}
		return null;
	}

	public Player getCurrentParticipant() {
		if (current_quest.participants.size() == 1)
			return current_quest.participants.get(0);
		return current_quest.participants.get(current_quest.participantTurns % current_quest.participants.size());
	}

	// convenience method because I think this will get called a lot
	public boolean isActiveAI() {
		return (this.getActivePlayer().isAI());
	}

	public int getWinCondition() {
		return winCondition;
	}

	public void setWinCondition(int winCondition) {
		this.winCondition = winCondition;
	}

	public void checkForTie() {
		// loop through player list checking for tieCheck = 1 (player var set after
		// champ knight achieved)
		// keep track of # of tieCheck and if 1 ---> end of game screen sole winner
		// if +1 then ---> end of game screen tie?
	}

	public String getPlayerStats() {
		String temp = "";
		for (int i = 0; i < players.size(); i++) {
			temp += players.get(i).getName() + ";" + players.get(i).getRank().getName() + ";"
					+ (players.get(i).getHandSize()) + ";" + players.get(i).shields + "#";
		}

		return temp;
	}

	public void setupNewPlayer(JsonObject jsonObject, WebSocketSession session) throws IOException {
		JsonElement playerName = jsonObject.get("newName");
		Player newPlayer = new Player(playerName.getAsString(), session);
		this.players.add(newPlayer);
		logger.info("Player {} is enrolled in the game", playerName.getAsString());
		if (this.players.size() == 4) {
			SocketHandler.sendToAllSessions(this, "GameReadyToStart");
			logger.info("All players have joined, starting game...");
			if (riggedGame != 0) {
				if (riggedGame == 42) {
					this.players.get(0).setHand(this.mockHand1); // pickUpNewHand()
					this.players.get(1).setHand(this.mockHand2);
					this.players.get(2).setHand(this.mockHand3);
					this.players.get(3).setHand(this.mockHand4);
				}
			} else {
				for (Player p : this.players) {
					p.pickupNewHand(this.adventureDeck);
				}
			}
			for (Player p : this.players) {
				p.session.sendMessage(new TextMessage("setHand" + p.getHandString()));
			}

			SocketHandler.flipStoryCard();
			String temp = this.getPlayerStats();
			SocketHandler.sendToAllSessions(this, "updateStats" + temp);

		}
	}

	public void getSponsor(JsonObject jsonObject) throws IOException {
		JsonElement sponsor_quest_answer = jsonObject.get("sponsor_quest");
		JsonElement name = jsonObject.get("name");
		if (sponsor_quest_answer.getAsBoolean()) {
			logger.info("{} accepted to sponsor quest {}", name.getAsString(), this.storyDeck.faceUp.getName());
			this.newQuest(this, this.getActivePlayer(), (QuestCard) this.storyDeck.faceUp);
		} else {
			logger.info("{} declined to sponsor quest {}", name.getAsString(), this.storyDeck.faceUp.getName());
			this.incTurn();
			if (this.getActivePlayer().equals(this.roundInitiater)) {
				SocketHandler.sendToAllSessions(this, "NoSponsors");
				logger.info("No players chose to sponsor {} quest", this.storyDeck.faceUp.getName());
				this.incTurn();
				this.getActivePlayer().session.sendMessage(new TextMessage("undisableFlip"));
				return;
			}
			this.getActivePlayer().session.sendMessage(new TextMessage("sponsorQuest"));
		}

	}

	public void updateStats() {
		String update = this.getPlayerStats();
		SocketHandler.sendToAllSessions(this, "updateStats" + update);

	}

}
