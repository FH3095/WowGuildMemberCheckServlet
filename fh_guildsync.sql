-- phpMyAdmin SQL Dump
-- version 4.9.0.1
-- https://www.phpmyadmin.net/
--
-- Host: localhost
-- Erstellungszeit: 07. Mrz 2020 um 01:15
-- Server-Version: 10.3.22-MariaDB-0+deb10u1
-- PHP-Version: 7.3.14-1~deb10u1

SET FOREIGN_KEY_CHECKS=0;
SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET AUTOCOMMIT = 0;
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `accounts`
--

CREATE TABLE `accounts` (
  `id` bigint(20) NOT NULL,
  `battle_net_id` bigint(20) DEFAULT NULL,
  `token` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `token_valid_until` datetime DEFAULT NULL
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

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `characters`
--

CREATE TABLE `characters` (
  `id` bigint(20) NOT NULL,
  `account_id` bigint(20) NOT NULL,
  `name` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `server` varchar(32) COLLATE utf8mb4_bin NOT NULL,
  `rank` int(11) NOT NULL DEFAULT 32767,
  `added` date NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

--
-- Indizes der exportierten Tabellen
--

--
-- Indizes für die Tabelle `accounts`
--
ALTER TABLE `accounts`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `idx_unique_battlenetid` (`battle_net_id`) USING BTREE;

--
-- Indizes für die Tabelle `account_remote_ids`
--
ALTER TABLE `account_remote_ids`
  ADD PRIMARY KEY (`account_id`,`remote_system_name`) USING BTREE,
  ADD UNIQUE KEY `idx_unique_remote_id` (`remote_system_name`,`remote_id`) USING BTREE;

--
-- Indizes für die Tabelle `characters`
--
ALTER TABLE `characters`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `idx_unique_char_server` (`name`,`server`) USING BTREE,
  ADD KEY `fk_characters_account_id` (`account_id`);

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
SET FOREIGN_KEY_CHECKS=1;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
