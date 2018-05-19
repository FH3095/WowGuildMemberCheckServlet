-- phpMyAdmin SQL Dump
-- version 4.6.6
-- https://www.phpmyadmin.net/
--
-- Host: localhost
-- Erstellungszeit: 13. Mai 2018 um 13:37
-- Server-Version: 10.1.26-MariaDB-0+deb9u1
-- PHP-Version: 7.0.27-0+deb9u1

SET FOREIGN_KEY_CHECKS=0;
SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET AUTOCOMMIT = 0;
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Datenbank: `fh_guildsync`
--

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `accounts`
--

CREATE TABLE `accounts` (
  `id` bigint(20) NOT NULL,
  `guild_id` bigint(20) NOT NULL,
  `battle_net_id` bigint(20) DEFAULT NULL,
  `token` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `token_valid_until` date DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `account_remote_ids`
--

CREATE TABLE `account_remote_ids` (
  `account_id` bigint(20) NOT NULL,
  `remote_system_name` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `remote_id` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

--
-- Trigger `account_remote_ids`
--
DELIMITER $$
CREATE TRIGGER `validate_unique_remote_system_and_remote_id_per_guild_on_insert` BEFORE INSERT ON `account_remote_ids` FOR EACH ROW BEGIN
	IF (SELECT COUNT(*) FROM account_remote_ids WHERE
      account_id <> NEW.account_id AND remote_system_name = NEW.remote_system_name AND remote_id = NEW.remote_id AND
      account_id IN (SELECT id FROM accounts WHERE guild_id IN (SELECT guild_id FROM accounts WHERE id = NEW.account_id))) > 0 THEN
        SET @message_text = concat('remote_system_name-remote_id ',NEW.remote_system_name,'-',NEW.remote_id,' not unique for guild of account ',NEW.account_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @message_text;
	END IF;
END
$$
DELIMITER ;
DELIMITER $$
CREATE TRIGGER `validate_unique_remote_system_and_remote_id_per_guild_on_update` BEFORE UPDATE ON `account_remote_ids` FOR EACH ROW BEGIN
	IF (SELECT COUNT(*) FROM account_remote_ids WHERE
      account_id <> NEW.account_id AND remote_system_name = NEW.remote_system_name AND remote_id = NEW.remote_id AND
      account_id IN (SELECT id FROM accounts WHERE guild_id IN (SELECT guild_id FROM accounts WHERE id = NEW.account_id))) > 0 THEN
        SET @message_text = concat('remote_system_name-remote_id ',NEW.remote_system_name,'-',NEW.remote_id,' not unique for guild of account ',NEW.account_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @message_text;
	END IF;
END
$$
DELIMITER ;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `characters`
--

CREATE TABLE `characters` (
  `id` bigint(20) NOT NULL,
  `account_id` bigint(20) NOT NULL,
  `name` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `server` varchar(32) COLLATE utf8mb4_bin NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `guilds`
--

CREATE TABLE `guilds` (
  `id` bigint(20) NOT NULL,
  `name` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `server` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `last_refresh` date DEFAULT NULL,
  `api_key` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `mac_key` varchar(44) COLLATE utf8mb4_bin NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `remote_commands`
--

CREATE TABLE `remote_commands` (
  `id` bigint(20) NOT NULL,
  `remote_system_name` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `account_id` bigint(20) NOT NULL,
  `command` enum('ACC_UPDATE') COLLATE utf8mb4_bin NOT NULL,
  `command_added` date NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

--
-- Indizes der exportierten Tabellen
--

--
-- Indizes für die Tabelle `accounts`
--
ALTER TABLE `accounts`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_guild_id` (`guild_id`);

--
-- Indizes für die Tabelle `account_remote_ids`
--
ALTER TABLE `account_remote_ids`
  ADD PRIMARY KEY (`account_id`,`remote_system_name`) USING BTREE,
  ADD KEY `idx_remote_id` (`remote_system_name`,`remote_id`) USING BTREE;

--
-- Indizes für die Tabelle `characters`
--
ALTER TABLE `characters`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `idx_unique_char_server` (`name`,`server`) USING BTREE,
  ADD KEY `fk_characters_account_id` (`account_id`);

--
-- Indizes für die Tabelle `guilds`
--
ALTER TABLE `guilds`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `idx_unique_name_server` (`name`,`server`) USING BTREE;

--
-- Indizes für die Tabelle `remote_commands`
--
ALTER TABLE `remote_commands`
  ADD PRIMARY KEY (`id`),
  ADD KEY `fk_remote_commands_account_id` (`account_id`),
  ADD KEY `idx_remote_commands_remote_system` (`remote_system_name`);

--
-- AUTO_INCREMENT für exportierte Tabellen
--

--
-- AUTO_INCREMENT für Tabelle `accounts`
--
ALTER TABLE `accounts`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT für Tabelle `characters`
--
ALTER TABLE `characters`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT für Tabelle `guilds`
--
ALTER TABLE `guilds`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT für Tabelle `remote_commands`
--
ALTER TABLE `remote_commands`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;
--
-- Constraints der exportierten Tabellen
--

--
-- Constraints der Tabelle `accounts`
--
ALTER TABLE `accounts`
  ADD CONSTRAINT `fk_accounts_guild_id` FOREIGN KEY (`guild_id`) REFERENCES `guilds` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints der Tabelle `account_remote_ids`
--
ALTER TABLE `account_remote_ids`
  ADD CONSTRAINT `fk_remote_id_account_id` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints der Tabelle `characters`
--
ALTER TABLE `characters`
  ADD CONSTRAINT `fk_characters_account_id` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints der Tabelle `remote_commands`
--
ALTER TABLE `remote_commands`
  ADD CONSTRAINT `fk_remote_commands_account_id` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`);
SET FOREIGN_KEY_CHECKS=1;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
