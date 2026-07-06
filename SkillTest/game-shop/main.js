/* 游戏数据 */
const games = [
  {
    id: 1,
    title: '黄泉之路',
    subtitle: 'Trek to Yomi',
    genre: '动作',
    genreSlug: 'action',
    price: 198.00,
    originalPrice: null,
    rating: 4.6,
    platform: 'PC · PS5 · Xbox',
    year: 2022,
    image: 'https://images.unsplash.com/photo-1538481199705-c710c4e965fc?w=600&h=338&fit=crop&auto=format',
    alt: '黑白水墨风格的武士持刀而立'
  },
  {
    id: 2,
    title: '星露谷物语',
    subtitle: 'Stardew Valley',
    genre: 'RPG',
    genreSlug: 'rpg',
    price: 48.00,
    originalPrice: null,
    rating: 4.9,
    platform: 'PC · Switch · 移动端',
    year: 2016,
    image: 'https://images.unsplash.com/photo-1558618666-fcd25c85f82e?w=600&h=338&fit=crop&auto=format',
    alt: '像素风格的田园风光'
  },
  {
    id: 3,
    title: '黑帝斯 II',
    subtitle: 'Hades II',
    genre: '动作',
    genreSlug: 'action',
    price: 139.00,
    originalPrice: null,
    rating: 4.8,
    platform: 'PC · PS5 · Xbox',
    year: 2024,
    image: 'https://images.unsplash.com/photo-1552820728-8b83bb6b2e2d?w=600&h=338&fit=crop&auto=format',
    alt: '暗黑风格的神话战斗场景'
  },
  {
    id: 4,
    title: '博德之门 3',
    subtitle: 'Baldur\'s Gate 3',
    genre: 'RPG',
    genreSlug: 'rpg',
    price: 298.00,
    originalPrice: 358.00,
    rating: 4.9,
    platform: 'PC · PS5 · Mac',
    year: 2023,
    image: 'https://images.unsplash.com/photo-1618336753974-aae8e04506aa?w=600&h=338&fit=crop&auto=format',
    alt: '奇幻世界中的冒险队伍'
  },
  {
    id: 5,
    title: '动物森友会',
    subtitle: 'Animal Crossing',
    genre: '模拟',
    genreSlug: 'adventure',
    price: 268.00,
    originalPrice: null,
    rating: 4.7,
    platform: 'Switch',
    year: 2020,
    image: 'https://images.unsplash.com/photo-1585314062340-f1a5a7c9328d?w=600&h=338&fit=crop&auto=format',
    alt: '色彩明亮的卡通小岛生活'
  },
  {
    id: 6,
    title: '文明 VI',
    subtitle: 'Civilization VI',
    genre: '策略',
    genreSlug: 'strategy',
    price: 199.00,
    originalPrice: 299.00,
    rating: 4.5,
    platform: 'PC · Switch · PS4',
    year: 2016,
    image: 'https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600&h=338&fit=crop&auto=format',
    alt: '俯瞰一座繁华的像素城市'
  },
  {
    id: 7,
    title: '空洞骑士',
    subtitle: 'Hollow Knight',
    genre: '动作',
    genreSlug: 'action',
    price: 58.00,
    originalPrice: null,
    rating: 4.9,
    platform: 'PC · Switch · PS4',
    year: 2017,
    image: 'https://images.unsplash.com/photo-1536746803623-cef87080bfc8?w=600&h=338&fit=crop&auto=format',
    alt: '幽暗的洞穴中一只小虫武士'
  },
  {
    id: 8,
    title: '蔚蓝',
    subtitle: 'Celeste',
    genre: '独立',
    genreSlug: 'indie',
    price: 68.00,
    originalPrice: null,
    rating: 4.8,
    platform: 'PC · Switch · PS4',
    year: 2018,
    image: 'https://images.unsplash.com/photo-1550745165-9bc0b252726f?w=600&h=338&fit=crop&auto=format',
    alt: '像素少女攀登粉色的山巅'
  },
  {
    id: 9,
    title: '只狼',
    subtitle: 'Sekiro: Shadows Die Twice',
    genre: '动作',
    genreSlug: 'action',
    price: 268.00,
    originalPrice: 398.00,
    rating: 4.7,
    platform: 'PC · PS5 · Xbox',
    year: 2019,
    image: 'https://images.unsplash.com/photo-1545987796-200677ee1011?w=600&h=338&fit=crop&auto=format',
    alt: '忍者站在古老的日本城楼顶端'
  }
];

/* 渲染游戏卡片 */
function renderGames(items) {
  const grid = document.getElementById('gameGrid');
  grid.innerHTML = items.map(game => {
    const priceDisplay = game.originalPrice
      ? `<span class="game-card__price game-card__price--sale">¥${game.price.toFixed(2)}</span>
         <span style="font-size:12px;color:var(--text-muted);text-decoration:line-through;margin-left:6px;">¥${game.originalPrice.toFixed(2)}</span>`
      : `<span class="game-card__price">¥${game.price.toFixed(2)}</span>`;

    const stars = '★'.repeat(Math.floor(game.rating)) + (game.rating % 1 >= 0.5 ? '★' : '');

    return `
      <div class="game-card" data-genre="${game.genreSlug}">
        <div class="game-card__image-wrap">
          <img
            class="game-card__image"
            src="${game.image}"
            alt="${game.alt}"
            loading="lazy"
          />
          <div class="game-card__strip">
            <span class="game-card__platform-tag">${game.platform}</span>
            <span class="game-card__year">${game.year}</span>
          </div>
        </div>
        <div class="game-card__info">
          <p class="game-card__genre">${game.genre}</p>
          <h3 class="game-card__title">${game.title}</h3>
          <p class="game-card__subtitle">${game.subtitle}</p>
          <div class="game-card__footer">
            <div>${priceDisplay}</div>
            <span class="game-card__rating">
              <span class="game-card__rating-star">${stars}</span>
              ${game.rating}
            </span>
          </div>
        </div>
      </div>
    `;
  }).join('');
}

/* 分类过滤 */
function filterGames(genre) {
  const cards = document.querySelectorAll('.game-card');
  cards.forEach(card => {
    if (genre === 'all' || card.dataset.genre === genre) {
      card.style.display = '';
    } else {
      card.style.display = 'none';
    }
  });
}

/* 筛选标签交互 */
document.querySelectorAll('.filter-pill').forEach(pill => {
  pill.addEventListener('click', () => {
    document.querySelector('.filter-pill--active').classList.remove('filter-pill--active');
    pill.classList.add('filter-pill--active');
    filterGames(pill.dataset.filter);
  });
});

/* 启动 */
renderGames(games);
