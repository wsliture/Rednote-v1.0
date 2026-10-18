/* 演示数据；不包含真实账号或凭据。 */
window.RednoteSeed = (() => {
    const photo = id => `images/${id}.jpg`;
    const pictures = ['photo-1441974231531-c6227db76b6e', 'photo-1495474472287-4d71bcdd2085', 'photo-1476514525535-07fb3b4ae5f1', 'photo-1449247709967-d4461a6a6103', 'photo-1498837167922-ddd27525d352', 'photo-1464822759023-fed622ff2c3b', 'photo-1449247709967-d4461a6a6103', 'photo-1470252649378-9c29740c9fa8', 'photo-1519681393784-d120267933ba', 'photo-1512621776951-a57141f2eefd', 'photo-1500534623283-312aade485b7', 'photo-1518837695005-2083093ee35b'];
    const titles = ['把周末交给山野，找回生活的呼吸感 🌿', '一杯手冲，开启不慌不忙的早晨', '走进湖边小镇，时间好像慢了下来', '家的角落｜把日子过成喜欢的样子', '认真吃饭，是给自己的温柔', '山的那边，还是值得奔赴的山', '在家也能拥有一平方米的松弛感', '落日是今天收到的最好礼物', '逃离城市，去看一场星空', '十分钟搞定！我的工作日轻食', '散步的时候，遇见了整个秋天', '听海的日子，什么都不用想'];
    const users = [{
        id: 1,
        username: 'linxia',
        name: '林小夏',
        bio: '记录日常，也收藏生活里的小小惊喜。\n杭州 · 摄影 / 咖啡 / 山野',
        role: '普通用户',
        status: '正常',
        date: '2026-10-01',
        color: '#e8dace'
    }, {
        id: 2,
        username: 'mumu',
        name: '木木的慢生活',
        bio: '好好吃饭，慢慢生活。',
        role: '普通用户',
        status: '正常',
        date: '2026-10-03',
        color: '#d6e4d4'
    }, {
        id: 3,
        username: 'yuchen',
        name: '宇辰在路上',
        bio: '下一站，去有风的地方。',
        role: '普通用户',
        status: '正常',
        date: '2026-10-07',
        color: '#d9e2eb'
    }, {
        id: 4,
        username: 'admin',
        name: 'Rednote 管理员',
        bio: '让每一份分享被看见。',
        role: '管理员',
        status: '正常',
        date: '2026-09-01',
        color: '#f9d8df'
    }, {
        id: 5,
        username: 'xiaoyu',
        name: '小鱼',
        bio: '日常记录者',
        role: '普通用户',
        status: '禁用',
        date: '2026-10-12',
        color: '#eadfcf'
    }];
    const notes = Array.from({length: 44}, (_, i) => ({
        id: i + 1,
        title: titles[i % 12],
        body: `${titles[i % 12]}\n\n最近越来越喜欢这样的日常。放下手里的事情，给自己一点留白，去感受阳光、微风和眼前的风景。\n\n不用赶路，也不用安排得太满。和喜欢的人一起，认真记录每一个普通却闪闪发光的瞬间。\n\n你最近发现了哪些生活里的美好？一起聊聊吧。`,
        author: i % 3 + 1,
        images: [photo(pictures[i % 12]), photo(pictures[(i + 2) % 12])],
        likes: 28 + (i * 73) % 960,
        date: `2026-10-${String(18 - Math.floor(i / 4)).padStart(2, '0')} 10:${String(59 - i).padStart(2, '0')}`,
        height: [290, 225, 330, 265, 240][i % 5]
    }));
    const comments = [{
        id: 1,
        note: 1,
        author: 2,
        text: '光看照片就感觉被治愈了！周末也想去走走 🌿',
        date: '10-18 11:20',
        replies: [{
            id: 2,
            author: 1,
            target: '木木的慢生活',
            text: '一定要去！山里的空气真的很舒服。',
            date: '10-18 11:28'
        }]
    }, {id: 3, note: 1, author: 3, text: '这样的周末才是充电，已经加入出行清单了。', date: '10-18 10:45', replies: []}];
    return {users, notes, comments};
})();
