// Banque de départ de la question de la semaine (PRD, récit 37), validée par l'admin principal le 15/11/2026 et
// revue avec le psychologue familial : https://claude.ai/artifact/SX2LFwekq2v8GzGeRJC8wF.
// Ids are stable: a published question keeps its bankId, so never renumber, only add at the end.

export interface BankQuestion {
  id: string;
  text: string;
  /** Seasonal questions only come out in these months (1 to 12), from [fromDay] to [toDay]. */
  season?: { months: number[]; fromDay?: number; toDay?: number };
}

const plain = (n: number, text: string): BankQuestion => ({ id: `q${String(n).padStart(2, '0')}`, text });

export const QUESTION_BANK: BankQuestion[] = [
  // Souvenirs
  plain(1, 'Ton meilleur souvenir avec quelqu\'un de plus âgé de la famille ?'),
  plain(2, 'Le plus beau cadeau qu\'on t\'ait jamais offert ?'),
  plain(3, 'Ta première fois à la mer, à la montagne ou en avion : tu t\'en souviens ?'),
  plain(4, 'Le jouet ou l\'objet que tu as gardé depuis ton enfance ?'),
  plain(5, 'Une bêtise que tu as faite petit et que personne n\'a jamais sue ?'),
  plain(6, 'Le repas de famille le plus mémorable ?'),
  plain(7, 'Ton plus beau voyage, et pourquoi ?'),
  plain(8, 'Une chanson qui te ramène directement en arrière ?'),
  plain(9, 'Le moment où tu as le plus ri en famille ?'),
  plain(10, 'Ton premier souvenir d\'école ?'),
  plain(11, 'Un conseil qu\'on t\'a donné et que tu suis encore ?'),
  plain(12, 'Une photo ancienne que tu aimes : raconte ce qu\'on y voit !'),
  // Goûts et habitudes
  plain(13, 'Ton plat préféré cuisiné par quelqu\'un de la famille ?'),
  plain(14, 'Ton petit-déjeuner idéal ?'),
  plain(15, 'Le dessert qui te fait craquer à tous les coups ?'),
  plain(16, 'Film, série ou dessin animé que tu pourrais revoir dix fois ?'),
  plain(17, 'Ton endroit préféré pour te détendre ?'),
  plain(18, 'La musique que tu écoutes en ce moment ?'),
  plain(19, 'Ton jeu de société ou jeu vidéo préféré ?'),
  plain(20, 'Le livre ou la BD qui t\'a marqué ?'),
  plain(21, 'Ton sport ou ton activité préférée ?'),
  plain(22, 'Matin ou soir ? Mer ou montagne ? Sucré ou salé ?'),
  plain(23, 'Ta chose préférée à faire le dimanche ?'),
  plain(24, 'L\'animal que tu aimerais avoir ?'),
  // Imagine…
  plain(25, 'Si tu avais un super-pouvoir, lequel ?'),
  plain(26, 'Si tu pouvais dîner avec un personnage célèbre, réel ou inventé, qui serait-ce ?'),
  plain(27, 'Si tu avais une journée entière rien que pour toi, que ferais-tu ?'),
  plain(28, 'Si tu pouvais vivre dans un autre pays pendant un an, lequel ?'),
  plain(29, 'Si notre famille était un film, quel serait son titre ?'),
  plain(30, 'Si tu pouvais remonter le temps, à quelle époque irais-tu ?'),
  plain(31, 'Si tu étais un animal, lequel serais-tu ?'),
  plain(32, 'Tu peux inventer une fête de famille : comment s\'appelle-t-elle et que fait-on ?'),
  plain(33, 'Ta maison de rêve, elle ressemble à quoi ?'),
  plain(34, 'Un métier que tu aurais adoré faire ?'),
  // La famille
  plain(35, 'Qu\'est-ce que tu as hérité de quelqu\'un de la famille (un trait, un geste, un talent) ?'),
  plain(36, 'Une tradition de famille que tu veux garder ?'),
  plain(37, 'Ce que tu admires chez quelqu\'un d\'un autre foyer ?'),
  plain(38, 'Un plat qu\'on ne mange qu\'en famille ?'),
  plain(39, 'Une expression ou une blague qu\'on ne comprend que dans la famille ?'),
  plain(40, 'Une recette que tu aimerais apprendre de quelqu\'un de la famille ?'),
  plain(41, 'Une activité qu\'on devrait faire tous ensemble cette année ?'),
  plain(42, 'Qu\'est-ce qui rend notre famille unique ?'),
  plain(43, 'Un merci que tu n\'as jamais dit à quelqu\'un de la famille ?'),
  plain(44, 'Un talent caché que tu as et que la famille ne connaît pas ?'),
  // Ta semaine
  plain(45, 'Ta petite victoire de la semaine ?'),
  plain(46, 'Ce qui t\'a fait sourire cette semaine ?'),
  plain(47, 'Une chose nouvelle que tu as apprise récemment ?'),
  plain(48, 'Décris ce que tu vois de ta fenêtre aujourd\'hui ?'),
  plain(49, 'Un objectif pour le mois qui vient ?'),
  plain(50, 'Une découverte (resto, lieu, appli, recette) à partager ?'),
  // Saisons
  { ...plain(51, 'Une bonne résolution, même petite ?'), season: { months: [1] } },
  { ...plain(52, 'Ton meilleur souvenir de neige ou d\'hiver ?'), season: { months: [2] } },
  { ...plain(53, 'Ce que tu as hâte de faire quand il fait beau ?'), season: { months: [3, 4] } },
  { ...plain(54, 'Tes vacances rêvées cette année ?'), season: { months: [5, 6] } },
  { ...plain(55, 'Ta glace préférée ?'), season: { months: [7, 8] } },
  { ...plain(56, 'Ce que tu attends de cette nouvelle année scolaire ou de travail ?'), season: { months: [9] } },
  { ...plain(57, 'Ton plat réconfortant préféré ?'), season: { months: [10, 11] } },
  { ...plain(58, 'Ton meilleur déguisement de tous les temps ?'), season: { months: [10], fromDay: 20 } },
  { ...plain(59, 'Ta tradition de Noël préférée ?'), season: { months: [12], toDay: 20 } },
  { ...plain(60, 'Ton meilleur moment de l\'année en famille ?'), season: { months: [12], fromDay: 21 } },
];
