/* 商品数据 */
const products = [
  {
    id: 1,
    name: '冻干鸡肉猫粮',
    category: '猫咪',
    categorySlug: 'cat',
    price: 68.00,
    image: 'https://images.unsplash.com/photo-1568640347023-a616a30bc3bd?w=400&h=300&fit=crop&auto=format',
    alt: '一碗冻干猫粮，旁边散落着冻干颗粒'
  },
  {
    id: 2,
    name: '硅胶宠物梳',
    category: '狗狗',
    categorySlug: 'dog',
    price: 45.00,
    image: 'https://images.unsplash.com/photo-1516734212186-a967f81ad0d7?w=400&h=300&fit=crop&auto=format',
    alt: '一把粉色硅胶梳子放在浅色木桌上'
  },
  {
    id: 3,
    name: '豆腐猫砂（原味）',
    category: '猫咪',
    categorySlug: 'cat',
    price: 29.90,
    image: 'https://images.unsplash.com/photo-1579113800032-c38bd7633291?w=400&h=300&fit=crop&auto=format',
    alt: '白色猫砂盆中装满干净的豆腐猫砂'
  },
  {
    id: 4,
    name: '可拆洗宠物窝',
    category: '狗狗',
    categorySlug: 'dog',
    price: 189.00,
    image: 'https://images.unsplash.com/photo-1592194996308-7b43878e84a6?w=400&h=300&fit=crop&auto=format',
    alt: '米白色圆形宠物窝，柔软厚实'
  },
  {
    id: 5,
    name: '瓦楞纸猫抓板',
    category: '猫咪',
    categorySlug: 'cat',
    price: 39.00,
    image: 'https://images.unsplash.com/photo-1545249390-6bdfa286032f?w=400&h=300&fit=crop&auto=format',
    alt: '猫抓板上趴着一只橘猫'
  },
  {
    id: 6,
    name: '防挣脱牵引绳',
    category: '狗狗',
    categorySlug: 'dog',
    price: 55.00,
    image: 'https://images.unsplash.com/photo-1601758228041-f3b2795255f1?w=400&h=300&fit=crop&auto=format',
    alt: '一条蓝色牵引绳挂在墙边挂钩上'
  },
  {
    id: 7,
    name: '兔用牧草干草',
    category: '小宠',
    categorySlug: 'small-pet',
    price: 28.00,
    image: 'https://images.unsplash.com/photo-1585110396000-c9ffd4e4b308?w=400&h=300&fit=crop&auto=format',
    alt: '一捧翠绿的提摩西干草'
  },
  {
    id: 8,
    name: '主食罐头（鸡肉味）',
    category: '食品',
    categorySlug: 'food',
    price: 18.50,
    image: 'https://images.unsplash.com/photo-1601758228041-f3b2795255f1?w=400&h=300&fit=crop&auto=format',
    alt: '金属罐头打开，露出鸡肉慕斯'
  }
];

/* 渲染商品卡片 */
function renderProducts(items) {
  const grid = document.getElementById('productGrid');
  grid.innerHTML = items.map(item => `
    <div class="product-card" data-category="${item.categorySlug}">
      <div class="product-card__image-wrap">
        <img
          class="product-card__image"
          src="${item.image}"
          alt="${item.alt}"
          loading="lazy"
        />
        <div class="product-card__overlay"></div>
        <span class="product-card__quick-view">Quick view</span>
      </div>
      <div class="product-card__info">
        <p class="product-card__category">${item.category}</p>
        <h3 class="product-card__name">${item.name}</h3>
        <p class="product-card__price">¥${item.price.toFixed(2)}</p>
      </div>
    </div>
  `).join('');
}

/* 分类过滤 */
function filterProducts(category) {
  const cards = document.querySelectorAll('.product-card');
  cards.forEach(card => {
    if (category === 'all' || card.dataset.category === category) {
      card.style.display = '';
    } else {
      card.style.display = 'none';
    }
  });
}

/* 分类标签交互 */
document.querySelectorAll('.pill').forEach(pill => {
  pill.addEventListener('click', () => {
    document.querySelector('.pill--active').classList.remove('pill--active');
    pill.classList.add('pill--active');
    filterProducts(pill.dataset.category);
  });
});

/* 启动 */
renderProducts(products);
