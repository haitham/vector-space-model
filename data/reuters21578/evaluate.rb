
results_path = "test_results#{(ARGV[0] == nil) ? "" : "/#{ARGV[0]}"}"

def precision(klass, cluster)
	numerator = klass.select{|k| cluster.include? k}.length
	return 0.0 if numerator.zero?
	numerator.to_f / cluster.length.to_f
end

def recall(klass, cluster)
	numerator = klass.select{|k| cluster.include? k}.length
	return 0.0 if numerator.zero?
	numerator.to_f / klass.length.to_f
end

def f_measure(klass, cluster)
	p = precision klass, cluster
	r = recall klass, cluster
	p * r == 0.0 ? 0.0 : 2 * p * r / (p + r)
end

def entropy(p)
	p == 0.0 ? 0.0 : p * Math.log(p)
end


def results(results_path)
	classes = []
	clusters = []
	f_measures = []
	entropies = []

	Dir.new(results_path).select{|name| name != "." and name != ".."}.each_with_index do |cluster_name, i|
		open "#{results_path}/#{cluster_name}", "r" do |cluster_file|
			clusters[i] = []
			while (line = cluster_file.gets)
				clusters[i] << line.strip
			end
		end
	end

	Dir.new("test_topics").select{|name| name != "." and name != ".."}.each_with_index do |class_name, i|
		open "test_topics/#{class_name}", "r" do |class_file|
			classes[i] = []
			while (line = class_file.gets)
				classes[i] << line.strip
			end
		end
	end



	clusters_left = clusters
	classes.each_with_index do |klass, i|
		cluster = clusters_left.max{|c1,c2| f_measure(klass, c1) <=> f_measure(klass, c2)}
		cluster = [] if cluster.nil?
		f_measures[i] = f_measure(klass, cluster)
		clusters_left = clusters_left.reject{|c| c == cluster}
	end



	clusters.each_with_index do |cluster, i|
		entropies[i] = -1.0 * classes.collect{|c| precision(c, cluster)}.inject(0.0){|s, p| s + entropy(p)}
	end



	f_total = 0.0
	f_measures.each_with_index do |f, i|
		f_total = f_total + f * classes[i].length
	end

	entropy_total = 0.0
	entropies.each_with_index do |e, i|
		entropy_total = entropy_total + e * clusters[i].length
	end
	
	f_result = f_total / classes.inject(0.0){|s, c| s + c.length}
	entropy_result = entropy_total / clusters.inject(0.0){|s, c| s + c.length}

	puts "F-measure:  #{f_result}"

	puts "Entropy:    #{entropy_result}"
	
	return {:f => f_result, :entropy => entropy_result}
end

puts "Evaluating results from #{results_path}"

open "test_results/evaluation.txt", "w" do |f|
	f.puts "K-means: F1\tK-means: Entropy\tHAC: F1\tHAC: Entropy"
	21.times.map{|a| a*0.05}.map{|alpha| {:k => results("#{results_path}/alpha=#{alpha}, {K=5.0}"), :hac => results("#{results_path}/alpha=#{alpha}, {size=5.0}")}}.each do |result|
		f.puts "#{result[:k][:f]}\t#{result[:k][:entropy]}\t#{result[:hac][:f]}\t#{result[:hac][:entropy]}"
	end
end





