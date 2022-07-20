<!--
parent:
  order: false
-->

<div align="center">
  <h1>FunctionX ($FX) - FXCore mainnet AutoCompounder (with FrenchXCore validator)</h1>
</div>
<p align="center">
  <img src="./src/main/resources/logo-functionx-730x482.jpeg" />
</p>

## Introduction

<p>FunctionX ($FX) is a decentralized ecosystem relying on the Cosmos SDK.</p>
<p>This software allows you to automatically restake your validator(s) commission fees and/or delegator(s) rewards, optimize your gains and reduce fees on the FXCore mainnet network.</p>

## Security

<p>Your seedphrases will be used and encrypted using AES-256 and a unique private password.</p>
<p style="font-weight:bold; background: red; text-align: center;">NEVER COMMUNICATE TO ANYONE YOUR CLEAR SEEDPHRASE, OR THIS PASSWORD AND YOUR ENCRYPTED SEEDPHRASE.</p>

## Side notes

<p>This software can be ran for multiple seedphrases and accounts.</p>
<p>Your rewards and commissions will be automatically delegated to FrenchXCore-1 validator.</p>
<p>The source code is made available for anyone to audit and make sure the seedphrases are never transmitted or stored locally in cleartext. It will also allow programmers to understand how to use the <a href="https://github.com/FrenchXCore/JFunctionxApi">JFunctionX API</a>.</p>
<p>Most transactions will be grouped altogether to reduce fees, but you need to make sure you always keep a minimum $FX on each account to process the transactions. By default, this software will restake all your rewards (and commission) except 5 $FX on each single account. Testing conducted to estimate the fee gains to approximately half of what you usually required.</p>
<p>When specifying multiple delegator addresses belonging to the same private key, only the first-512 derived FX addresses are accepted.</p>
<p>This software is available for free, but <span style="font-weight: bolder">USE IT AT YOUR OWN RISK</span>.</p>

## Licence

<p>This software is released under GPL v3.0 licence, available <a href="https://www.gnu.org/licenses/gpl-3.0.html">here</a>.</p>
<p>We only ask you to quote the original author (<a href="https://twitter.com/FrenchXCore1">FrenchXCore</a>) and the source code URL in case you modify it, distribute it...</p>

## Quick start

<p>Before anything, you need to encrypt your private seedphrase(s) using a root private password (that you will keep somewhere safe).</p>
<p>To do so, just run: <br/><code>java -jar FXAutoCompounder.jar encrypt</code><br/>and follow instructions...</p>
<p>Repeat as required if you want to generate multiple encrypted seedphrases to manage multiple accounts at once.</p>
<p>You will then be able to use this/these encrypted seedphrase(s) and this password to run the FX AutoCompounder.</p>
<p>In the following examples, we also provide a node that can be used to run the transactions using <code>-n 167.86.101.244</code> switch.</p>
<p>Don't forget to run that software in a specific shell console, eventually using 'screen' tool (<code>sudo apt-get install screen</code> then using <code>screen</code>, <code>screen -r</code> or <code>[CTRL]+a</code> then <code>d</code>) for Linux users.</p>
<p>You can specify as many accounts as required as long as you provide the encrypted seedphrases (all encrypted with the same private root password).</p>
<p>By default, the software will always keep 5 $FX on your accounts and restake only if 100 $FX can be withdrawn and delegated.</p>
<p>For more options, see "Other options" below.</p>

### Example #1 : One delegator auto-compounding
<p>Run as per this example: <br/><code>java -jar FXAutoCompounder.jar autocompound -n 167.86.101.244 -d <i>YOUR-FX-DELEGATOR-ADDRESS</i> -s <i>YOUR-ENCRYPTED-SEEDPHRASE</i></code></p>
<p>Your encrypted seedphrase must be copied right after the '-s' switch.</p>
<p>Your delegator addresses must be specified after the '-d'.</p>

### Example #2 : Multiple delegators auto-compounding (from the same seedphrase)
<p>Your encrypted seedphrase must be copied right after the '-s' switch.</p>
<p>Your delegator addresses must be specified after the '-d' switch and separated with a ':'.</p>
<p>Run as per this example: <br/><code>java -jar FXAutoCompounder.jar autocompound -n 167.86.101.244 -d <i>YOUR-FX-DELEGATOR-ADDRESS-#1</i>:<i>YOUR-FX-DELEGATOR-ADDRESS-#2</i> -s <i>YOUR-ENCRYPTED-SEEDPHRASE</i></code></p>

### Example #3 : Multiple delegators auto-compounding (from different accounts/seedphrases)
<p>Your encrypted seedphrases must be copied right after the '-s' switch and separated with a ':'.</p>
<p>Your delegator addresses must be specified after the '-d' switch and separated with a ':'.</p>
<p>Run as per this example: <br/><code>java -jar FXAutoCompounder.jar autocompound -n 167.86.101.244 -d <i>YOUR-FX-DELEGATOR-ADDRESS-#1</i>:<i>YOUR-FX-DELEGATOR-ADDRESS-#2</i> -s <i>YOUR-ENCRYPTED-SEEDPHRASE-#1:YOUR-ENCRYPTED-SEEDPHRASE-#2</i></code></p>

### Example #4 : Validator commission and self-bound delegator rewards auto-compounding
<p>The validator's self-bonded delegator will be added automatically.</p>
<p>Your encrypted seedphrase must be copied right after the '-s' switch.</p>
<p>The delegator addresses must be specified after the '-d' switch and separated with a ':'.</p>
<p>Run as per this example: <br/><code>java -jar FXAutoCompounder.jar autocompound -n 167.86.101.244 -v <i>YOUR-VALIDATOR-ADDRESS</i> -s <i>YOUR-ENCRYPTED-SEEDPHRASE</i></code></p>

### Example #5 : Multiple validator commissions and self-bound delegators rewards auto-compounding (from different accounts/seedphrases)
<p>The validators self-bonded delegators will be added automatically.</p>
<p>Your encrypted seedphrases must be copied right after the '-s' switch and separated with a ':'.</p>
<p>Run as per this example: <br/><code>java -jar FXAutoCompounder.jar autocompound -n 167.86.101.244 -v <i>YOUR-FX-VALIDATOR-ADDRESS-#1</i>:<i>YOUR-FX-VALIDATOR-ADDRESS-#2</i> -s <i>YOUR-ENCRYPTED-SEEDPHRASE-#1:YOUR-ENCRYPTED-SEEDPHRASE-#2</i></code></p>

### Example #6 : Multiple validator commissions and self-bound delegators rewards, along with multiple other delegators, auto-compounding (from different accounts/seedphrases)
<p>The validators self-bonded delegators will be added automatically.</p>
<p>Your encrypted seedphrases must be copied right after the '-s' switch and separated with a ':'.</p>
<p>Run as per this example: <br/><code>java -jar FXAutoCompounder.jar autocompound -n 167.86.101.244 -v <i>YOUR-FX-VALIDATOR-ADDRESS-#1</i>:<i>YOUR-FX-VALIDATOR-ADDRESS-#2</i> -d <i>YOUR-FX-DELEGATOR-ADDRESS-#3</i>:<i>YOUR-FX-DELEGATOR-ADDRESS-#4</i> -s <i>YOUR-ENCRYPTED-SEEDPHRASE-#1:YOUR-ENCRYPTED-SEEDPHRASE-#2:YOUR-ENCRYPTED-SEEDPHRASE-#3:YOUR-ENCRYPTED-SEEDPHRASE-#4</i></code></p>

## Other options

<p>You can specify many other options :</p>
<ul>
<li>'-m': the minimum cumulated $FX rewards (and commission fees) before withdrawing and restaking (set by default to 100 $FX). You can specify a decimal number: for example "123.45". Minimum: 10.</li>
<li>'-k': the minimum $FX to keep unstaked on each FX delegator address (set by default to 5 $FX). You can specify a decimal number: for example "3.45". Minimum: 2.</li>
<li>'-t': the period (in seconds) when balance, rewards and commissions will be checked (and restaked if they meet the specified requirements) - Maximum: 86400.</li>
<li>'-n': your preferred FXCore mainnet node IP address</li>
<li>'-p': your preferred FXCore mainnet node Cosmos-gRPC port number</li>
</ul>

## Updates
- v1.0 : initial version, compatible with FXCore mainnet V2.1.1

## What's next ?
- Almost the same, but decentralized ?

Have fun !